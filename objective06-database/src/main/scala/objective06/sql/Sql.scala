package objective06.sql

import scala.collection.mutable

/** SQL subset (O6): lexer + AST + recursive-descent parser.
  *
  * Supported:
  *   CREATE TABLE t (c INT|REAL|TEXT|BOOL, ...)
  *   CREATE INDEX i ON t (c)
  *   INSERT INTO t VALUES (lit, ...)
  *   SELECT c1,c2 FROM t [WHERE pred]
  *   UPDATE t SET c=expr [, ...] [WHERE pred]
  *   DELETE FROM t [WHERE pred]
  *   SELECT agg(c) FROM t   (COUNT, SUM, AVG, MIN, MAX)
  */

// ---------- AST ----------
sealed trait Sql
case class CreateTable(name:String, cols:Vector[(String,String)]) extends Sql
case class CreateIndex(name:String, table:String, col:String) extends Sql
case class Insert(table:String, values:Vector[SqlExpr]) extends Sql
case class Select(cols:Vector[SqlExpr], table:String, where:Option[SqlExpr],
                  groupBy:Vector[String], having:Option[SqlExpr], distinct:Boolean) extends Sql
case class Update(table:String, sets:Vector[(String,SqlExpr)], where:Option[SqlExpr]) extends Sql
case class Delete(table:String, where:Option[SqlExpr]) extends Sql
case class DropTable(name:String) extends Sql

sealed trait SqlExpr
case class Col(name:String) extends SqlExpr
case class Lit(v:Any) extends SqlExpr            // Int/Double/String/Boolean
case class Agg(fn:String, arg:SqlExpr) extends SqlExpr
case class Bin(op:String,l:SqlExpr,r:SqlExpr) extends SqlExpr
case class Un(op:String,e:SqlExpr) extends SqlExpr
case class IsNull(e:SqlExpr, neg:Boolean) extends SqlExpr
case class Between(e:SqlExpr,lo:SqlExpr,hi:SqlExpr,neg:Boolean) extends SqlExpr
case class InList(e:SqlExpr,vs:Vector[SqlExpr],neg:Boolean) extends SqlExpr
case class Star() extends SqlExpr

// ---------- Lexer ----------
object SqlLexer {
  sealed trait Tok
  case class Id(s:String) extends Tok
  case class Num(n:Double) extends Tok
  case class Str(s:String) extends Tok
  case class Kw(s:String) extends Tok
  case class Op(s:String) extends Tok
  case object Eof extends Tok

  private val KW = Set("SELECT","FROM","WHERE","INSERT","INTO","VALUES","UPDATE","SET",
    "DELETE","CREATE","TABLE","INDEX","ON","DROP","AND","OR","NOT","NULL","AS",
    "INT","REAL","TEXT","BOOL","COUNT","SUM","AVG","MIN","MAX","DISTINCT","BY","HAVING","IN","BETWEEN","IS")

  def tokenize(s:String):Vector[Tok] = {
    val out=mutable.ArrayBuffer[Tok](); var i=0; val n=s.length
    while(i<n){
      val c=s(i)
      if(c.isWhitespace){i+=1}
      else if(c=='\''){ var j=i+1; val sb=new StringBuilder
        while(j<n&&s(j)!='\''){ if(s(j)=='\\'&&j+1<n){sb.append(s(j+1));j+=2}else{sb.append(s(j));j+=1} }
        out+=Str(sb.toString); i=j+1 }
      else if(c.isDigit|| (c=='.'&&i+1<n&&s(i+1).isDigit)){ var j=i; val sb=new StringBuilder
        while(j<n&&(s(j).isDigit||s(j)=='.')){sb.append(s(j));j+=1}; out+=Num(sb.toString.toDouble); i=j }
      else if(c.isLetter||c=='_'){ var j=i; val sb=new StringBuilder
        while(j<n&&(s(j).isLetterOrDigit||s(j)=='_')){sb.append(s(j));j+=1}
        val w=sb.toString; val up=w.toUpperCase
        if(KW.contains(up)) out+=Kw(up) else out+=Id(w); i=j }
      else if(i+1<n && Set("<=",">=","!=","<>","==").contains(s.substring(i,i+2))){out+=Op(s.substring(i,i+2));i+=2}
      else if("(),;*+-/%=<>".contains(c)){out+=Op(c.toString);i+=1}
      else i+=1 // skip unknown
    }
    out+=Eof; out.toVector
  }
}

// ---------- Parser ----------
class SqlParser(toks:Vector[SqlLexer.Tok]){
  import SqlLexer._
  private var p=0
  private def peek= toks(p)
  private def next={val t=toks(p); if(p<toks.size-1)p+=1; t}
  private def atKw(k:String)= peek match {case Kw(s)=>s==k;case _=>false}
  private def eatKw(k:String)= if(atKw(k)){next;true}else false
  private def atOp(o:String)= peek match {case Op(s)=>s==o;case _=>false}
  private def eatOp(o:String)= if(atOp(o)){next;true}else false
  private def expectOp(o:String)={ if(!eatOp(o)) throw new Exception(s"expected '$o' got $peek"); }
  private def ident():String = peek match {case Id(s)=>{next;s};case Kw(s) if s=="COUNT"||s=="SUM"||s=="AVG"||s=="MIN"||s=="MAX"=>{next;s.toLowerCase};case _=>throw new Exception(s"expected ident got $peek")}
  private def typeName():String = peek match {
    case Id(s)=>{next;s.toUpperCase}
    case Kw(s) if Set("INT","REAL","TEXT","BOOL").contains(s)=>{next;s}
    case _=>throw new Exception(s"expected type got $peek")
  }

  def parse():Sql = {
    if(eatKw("CREATE")){ if(eatKw("TABLE")) parseCreateTable() else if(eatKw("INDEX")) parseCreateIndex() else throw new Exception("expected TABLE/INDEX after CREATE") }
    else if(eatKw("INSERT")){ eatKw("INTO"); val t=ident()
      // optional column list ( ... ) ; VALUES may follow directly
      if(atOp("(")){ next; var d=1; while(d>0&&peek!=Eof){ peek match{case Op("(")=>{next;d+=1};case Op(")")=>{next;d-=1};case _=>next} } }
      eatKw("VALUES"); expectOp("("); val vs=exprList(")"); expectOp(")"); Insert(t,vs) }
    else if(eatKw("SELECT")) parseSelect()
    else if(eatKw("UPDATE")){ val t=ident(); expectOp("SET"); val sets=mutable.ArrayBuffer[(String,SqlExpr)]()
      var cont=true; while(cont){ val c=ident(); expectOp("="); sets+=((c,expr())); cont=eatOp(",") }; Update(t,sets.toVector,optWhere()) }
    else if(eatKw("DELETE")){ eatKw("FROM"); val t=ident(); Delete(t,optWhere()) }
    else if(eatKw("DROP")){ eatKw("TABLE"); DropTable(ident()) }
    else throw new Exception(s"unexpected $peek")
  }

  private def parseCreateTable():Sql = { val n=ident(); expectOp("("); val cols=mutable.ArrayBuffer[(String,String)]()
    var cont=true; while(cont){ val c=ident(); val ty=typeName(); cols+=((c,ty)); cont=eatOp(",") }; expectOp(")"); CreateTable(n,cols.toVector) }
  private def parseCreateIndex():Sql = { val n=ident(); eatKw("ON"); val t=ident(); expectOp("("); val c=ident(); expectOp(")"); CreateIndex(n,t,c) }

  private def parseSelect():Sql = {
    val distinct = eatKw("DISTINCT")
    val cols=mutable.ArrayBuffer[SqlExpr]()
    if(eatOp("*")) cols+=Star() else { cols+=expr(); while(eatOp(",")) cols+=expr() }
    eatKw("FROM"); val table=ident()
    val where=optWhere()
    var groupBy=Vector.empty[String]; var having:Option[SqlExpr]=None
    if(eatKw("GROUP")){ eatKw("BY"); val g=mutable.ArrayBuffer[String](); g+=ident(); while(eatOp(","))g+=ident(); groupBy=g.toVector }
    if(eatKw("HAVING")) having=Some(expr())
    Select(cols.toVector,table,where,groupBy,having,distinct)
  }
  private def optWhere():Option[SqlExpr]= if(eatKw("WHERE")) Some(expr()) else None

  private def exprList(end:String):Vector[SqlExpr]= { val vs=mutable.ArrayBuffer[SqlExpr](); vs+=expr(); while(eatOp(","))vs+=expr(); vs.toVector }

  // precedence climbing
  private def expr():SqlExpr = orExpr()
  private def orExpr():SqlExpr = { var l=andExpr(); while(eatKw("OR")){l=Bin("OR",l,andExpr())}; l }
  private def andExpr():SqlExpr = { var l=notExpr(); while(eatKw("AND")){l=Bin("AND",l,notExpr())}; l }
  private def notExpr():SqlExpr = if(eatKw("NOT")) Un("NOT",notExpr()) else cmpExpr()
  private def cmpExpr():SqlExpr = {
    var l=addExpr()
    // IS [NOT] NULL, BETWEEN, IN, comparisons
    if(eatKw("IS")){ val neg=eatKw("NOT"); eatKw("NULL"); return IsNull(l,neg) }
    if(eatKw("BETWEEN")){ val lo=addExpr(); eatKw("AND"); val hi=addExpr(); val neg=false; return Between(l,lo,hi,neg) }
    if(eatKw("IN")){ expectOp("("); val vs=exprList(")"); expectOp(")"); return InList(l,vs,false) }
    val ops=Set("=","!=","<>","<",">","<=",">=")
    peek match { case Op(o) if ops(o)=>{next; val r=addExpr(); Bin(if(o=="<>")"!="else o,l,r)}; case _=>l }
  }
  private def addExpr():SqlExpr = { var l=mulExpr(); while(peek match{case Op(o)=>o=="+"||o=="-";case _=>false}){val o=next.asInstanceOf[Op].s;l=Bin(o,l,mulExpr())}; l }
  private def mulExpr():SqlExpr = { var l=unary(); while(peek match{case Op(o)=>o=="*"||o=="/"||o=="%";case _=>false}){val o=next.asInstanceOf[Op].s;l=Bin(o,l,unary())}; l }
  private def unary():SqlExpr = if(eatOp("-")) Un("-",unary()) else if(eatOp("+")) unary() else primary()
  private def primary():SqlExpr = peek match {
    case Num(n)=>{next;Lit(n)}
    case Str(s)=>{next;Lit(s)}
    case Kw("TRUE")=>{next;Lit(true)}
    case Kw("FALSE")=>{next;Lit(false)}
    case Kw(k) if Set("COUNT","SUM","AVG","MIN","MAX").contains(k)=>{next;expectOp("(");val a=expr();expectOp(")");Agg(k,a)}
    case Id(s)=>{next;Col(s)}
    case Op("(")=>{next;val e=expr();expectOp(")");e}
    case Op("*")=>{next;Star()}
    case _=>{next;Lit(0)}
  }
}

object Sql {
  def parse(s:String):Sql = new SqlParser(SqlLexer.tokenize(s)).parse()
}
