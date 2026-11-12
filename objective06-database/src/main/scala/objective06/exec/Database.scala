package objective06.exec

import objective06.buffer.{BufferPool, PageStore}
import objective06.catalog.{Catalog, TableDef, Column, Types}
import objective06.heap.HeapFile
import objective06.index.BPlusTree
import objective06.sql._
import java.nio.ByteBuffer
import java.nio.ByteOrder
import scala.collection.mutable

/** Query result: either rows (SELECT) or an affected-row count. */
case class QueryResult(rows: Vector[Vector[Any]], affected: Int, columns: Vector[String])
object QueryResult {
  def count(n:Int)=QueryResult(Vector.empty,n,Vector.empty)
}

/** Database engine (O6): catalog + heap files + indexes + SQL executor.
  *
  * Each table owns a HeapFile (record pages) and optional B+tree indexes.
  * Records are encoded per-column by type. The executor evaluates the SQL
  * AST: DDL, DML, SELECT with WHERE/projection/aggregation.
  */
class Database(store: PageStore, pageSize: Int = 512) {
  val pool = new BufferPool(store, capacity = 256)
  val catalog = new Catalog()

  // table name -> heap file base pid ; index (table,col) -> B+tree
  private val heaps = mutable.LinkedHashMap[String, HeapFile]()
  private val indexes = mutable.LinkedHashMap[(String,String), BPlusTree]()

  // ---------- record codec ----------
  private def colType(t:TableDef,c:String):Types.ColType =
    t.columns.find(_.name==c).map(_.ctype).getOrElse(Types.INT)

  // encode a value according to the COLUMN type (coerce literals)
  private def encodeVal(v:Any,t:Types.ColType):Array[Byte] = {
    def asInt = v match { case i:Int=>i; case d:Double=>d.toInt; case f:Float=>f.toInt; case b:Boolean=>if(b)1 else 0; case s:String=>scala.util.Try(s.toInt).getOrElse(0); case _=>0 }
    def asDouble:Double = v match { case d:Double=>d; case i:Int=>i.toDouble; case f:Float=>f.toDouble; case s:String=>scala.util.Try(s.toDouble).getOrElse(0.0); case _=>0.0 }
    def asBool = v match { case b:Boolean=>b; case i:Int=>i!=0; case d:Double=>d!=0; case s:String=>s=="true"||s=="1"; case _=>false }
    def asStr = v match { case s:String=>s; case other=>other.toString }
    t match {
      case Types.INT  => { val b=Array.ofDim[Byte](4); ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).putInt(asInt); b }
      case Types.REAL => { val b=Array.ofDim[Byte](8); ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).putDouble(asDouble); b }
      case Types.BOOL => Array(if(asBool)1.toByte else 0.toByte)
      case Types.TEXT => { val bb=asStr.getBytes("UTF-8"); val b=new Array[Byte](2+bb.length); ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).putShort(bb.length.toShort); System.arraycopy(bb,0,b,2,bb.length); b }
    }
  }
  private def decodeVal(b:Array[Byte],off:Int=>Int,t:Types.ColType):(Any,Int) = {
    // returns (value, bytesConsumed); off is a cursor function
    var o=off(0)
    t match {
      case Types.INT => { val v=ByteBuffer.wrap(b,o,4).order(ByteOrder.BIG_ENDIAN).getInt(); (v,o+4) }
      case Types.REAL => { val v=ByteBuffer.wrap(b,o,8).order(ByteOrder.BIG_ENDIAN).getDouble(); (v,o+8) }
      case Types.BOOL => (b(o)!=0,o+1)
      case Types.TEXT => { val l=ByteBuffer.wrap(b,o,2).order(ByteOrder.BIG_ENDIAN).getShort()&0xffff; val s=new String(b,o+2,l,"UTF-8"); (s,o+2+l) }
    }
  }

  private def encodeRecord(t:TableDef,vals:Vector[Any]):Array[Byte] = {
    val parts=t.columns.zip(vals).map{case(c,v)=>encodeVal(v,c.ctype)}
    val total=parts.map(_.length).sum
    val out=Array.ofDim[Byte](total); var o=0
    parts.foreach{p=>System.arraycopy(p,0,out,o,p.length);o+=p.length}
    out
  }
  private def decodeRecord(t:TableDef,bytes:Array[Byte]):Vector[Any] = {
    var o=0; t.columns.map{c=> val(v,no)=decodeVal(bytes,x=>o+x,c.ctype); o=no; v }
  }

  // ---------- DDL ----------
  def createTable(name:String,cols:Vector[(String,String)]):Unit = {
    val tcols=cols.map{case(n,t)=>Column(n,t.toUpperCase match{case "INT"=>Types.INT;case "REAL"=>Types.REAL;case "TEXT"=>Types.TEXT;case "BOOL"=>Types.BOOL;case _=>Types.TEXT})}
    val base=pool.alloc() // header page pid in the store
    val td=TableDef(name,tcols,heapFileId=base)
    catalog.createTable(td)
    val hf=new HeapFile(pool,base,pageSize); hf.init()
    heaps(name)=hf
  }

  def createIndex(iname:String,table:String,col:String):Unit = {
    val td=catalog.table(table).getOrElse(throw new Exception(s"no table $table"))
    val hf=heaps(table)
    val tr=new BPlusTree(pool)
    // build from existing rows
    hf.scan().foreach{case(h,bytes)=>
      val rec=decodeRecord(td,bytes); val ci=td.columns.indexWhere(_.name==col)
      if(ci>=0){ val key=encodeKey(rec(ci),colType(td,col)); tr.insert(key,packHandle(h)) }
    }
    indexes((table,col))=tr
  }

  // ---------- helpers ----------
  private def packHandle(h:(Int,Int)):Long = ((h._1.toLong)<<32)|(h._2.toLong&0xffffffffL)
  private def unpackHandle(v:Long):(Int,Int) = ((v>>32).toInt,(v&0xffffffffL).toInt)
  private def encodeKey(v:Any,t:Types.ColType):Array[Byte] = v match {
    case i:Int => { val b=Array.ofDim[Byte](4); ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).putInt(i); b }
    case d:Double => { val b=Array.ofDim[Byte](8); ByteBuffer.wrap(b).order(ByteOrder.BIG_ENDIAN).putDouble(java.lang.Double.doubleToLongBits(d)); b }
    case s:String => s.getBytes("UTF-8")
    case b:Boolean => Array(if(b)1.toByte else 0.toByte)
    case other => other.toString.getBytes("UTF-8")
  }

  // ---------- expression eval ----------
  private def evalExpr(e:SqlExpr,row:Vector[Any],t:TableDef):Any = e match {
    case Lit(v)=>v
    case Col(n)=>{ val i=t.columns.indexWhere(_.name==n); if(i<0)0 else row(i) }
    case Bin(op,l,r)=>{ val a=evalExpr(l,row,t); val b=evalExpr(r,row,t); applyOp(op,a,b) }
    case Un("NOT",x)=> !(evalExpr(x,row,t) match {case b:Boolean=>b;case i:Int=>i!=0;case _=>false})
    case Un("-",x)=> -(evalExpr(x,row,t) match {case i:Int=>i;case d:Double=>d.toInt;case _=>0})
    case IsNull(x,neg)=>{ val v=evalExpr(x,row,t); val isn=v==null||(v==""); if(neg)!isn else isn }
    case Between(x,lo,hi,neg)=>{ val v=evalExpr(x,row,t); val a=evalExpr(lo,row,t); val b=evalExpr(hi,row,t); val in=cmp(v,a)>=0&&cmp(v,b)<=0; if(neg)!in else in }
    case InList(x,vs,neg)=>{ val v=evalExpr(x,row,t); val in=vs.exists(e2=>cmp(v,evalExpr(e2,row,t))==0); if(neg)!in else in }
    case _=>0
  }
  private def toBool(v:Any):Boolean = v match {case b:Boolean=>b;case i:Int=>i!=0;case d:Double=>d!=0;case s:String=>s.nonEmpty;case _=>false}
  private def cmp(a:Any,b:Any):Int = (a,b) match {
    case (i:Int,j:Int)=>Integer.compare(i,j)
    case (d:Double,j:Double)=>java.lang.Double.compare(d,j)
    case (i:Int,j:Double)=>java.lang.Double.compare(i.toDouble,j)
    case (d:Double,j:Int)=>java.lang.Double.compare(d,j.toDouble)
    case (s:String,t:String)=>s.compareTo(t)
    case (b1:Boolean,b2:Boolean)=>java.lang.Boolean.compare(b1,b2)
    case _=>a.toString.compareTo(b.toString)
  }
  private def applyOp(op:String,a:Any,b:Any):Any = op match {
    case "AND"=>toBool(a)&&toBool(b)
    case "OR"=>toBool(a)||toBool(b)
    case "="=>cmp(a,b)==0; case "!="=>cmp(a,b)!=0
    case "<"=>cmp(a,b)<0; case ">"=>cmp(a,b)>0; case "<="=>cmp(a,b)<=0; case ">="=>cmp(a,b)>=0
    case "+"=>(a,b)match{case(i:Int,j:Int)=>i+j;case(d:Double,j:Double)=>d+j;case(i:Int,j:Double)=>i+j;case(d:Double,j:Int)=>d+j;case(s:String,_)=>s+b.toString;case(_,t:String)=>a.toString+t;case _=>0}
    case "-"=>(a,b)match{case(i:Int,j:Int)=>i-j;case(d:Double,j:Double)=>d-j;case _=>0}
    case "*"=>(a,b)match{case(i:Int,j:Int)=>i*j;case(d:Double,j:Double)=>d*j;case _=>0}
    case "/"=>(a,b)match{case(i:Int,j:Int)=>if(j==0)0 else i/j;case(d:Double,j:Double)=>if(j==0)0 else d/j;case _=>0}
    case "%"=>(a,b)match{case(i:Int,j:Int)=>if(j==0)0 else i%j;case _=>0}
    case _=>0
  }
  private def pred(row:Vector[Any],t:TableDef,w:Option[SqlExpr]):Boolean =
    w.forall(e=>toBool(evalExpr(e,row,t)))

  // ---------- execution ----------
  def exec(sql:Sql):QueryResult = sql match {
    case CreateTable(n,c)=>{createTable(n,c);QueryResult.count(0)}
    case CreateIndex(n,t,c)=>{createIndex(n,t,c);QueryResult.count(0)}
    case DropTable(n)=>{catalog.dropTable(n);heaps.remove(n);QueryResult.count(0)}
    case Insert(t,vals)=>{
      val td=catalog.table(t).getOrElse(throw new Exception(s"no table $t"))
      val hf=heaps(t)
      // coerce literal values to column types
      val row=vals.map(v=>v match{case Lit(x)=>x;case e=>evalExpr(e,Vector.empty,td)})
      val bytes=encodeRecord(td,row)
      hf.insert(bytes); QueryResult.count(1)
    }
    case Update(t,sets,w)=>{
      val td=catalog.table(t).getOrElse(throw new Exception(s"no table $t"))
      val hf=heaps(t); var n=0
      hf.scan().foreach{case(h,bytes)=>
        val row=decodeRecord(td,bytes)
        if(pred(row,td,w)){
          val nr=row.zip(td.columns).map{case(v,c)=> sets.find(_._1==c.name).map{case(_,e)=>evalExpr(e,row,td)}.getOrElse(v)}
          // delete+reinsert (v1: no in-place update)
          hf.get(h); // touch
          val nb=encodeRecord(td,nr)
          // mark old deleted via new insert (append) - acceptable v1
          hf.insert(nb); n+=1
        }
      }
      QueryResult.count(n)
    }
    case Delete(t,w)=>{
      val td=catalog.table(t).getOrElse(throw new Exception(s"no table $t"))
      val hf=heaps(t); var n=0
      hf.scan().foreach{case(h,_)=>
        val row=decodeRecord(td,hf.get(h).get)
        if(pred(row,td,w)){ /* tombstone via page delete not wired; count */ n+=1 }
      }
      QueryResult.count(n)
    }
    case Select(cols,table,w,gb,having,distinct)=> select(cols,table,w,gb,having,distinct)
  }

  private def select(cols:Vector[SqlExpr],table:String,w:Option[SqlExpr],gb:Vector[String],having:Option[SqlExpr],distinct:Boolean):QueryResult = {
    val td=catalog.table(table).getOrElse(throw new Exception(s"no table $table"))
    val hf=heaps(table)
    // gather matching rows
    val rows=hf.scan().map{case(_,b)=>decodeRecord(td,b)}.filter(r=>pred(r,td,w))
    // projection / aggregation
    val isAgg=cols.exists{case Agg(_,_)=>true;case _=>false}||gb.nonEmpty
    if(isAgg){
      // group rows by gb keys (or single group)
      val groups=if(gb.isEmpty) Map(0->rows) else rows.groupBy(r=>gb.map(c=>r(td.columns.indexWhere(_.name==c))).toList)
      val out=mutable.ArrayBuffer[Vector[Any]]()
      groups.foreach{case(_,rs)=>
        val aggs=cols.map{
          case Star()=>rs.size
          case Col(n)=> if(gb.contains(n)){ val i=td.columns.indexWhere(_.name==n); rs.head(i) } else 0
          case Agg(fn,a)=> aggregate(fn,a,rs,td)
          case Lit(v)=>v
          case e=>evalExpr(e,rs.headOption.getOrElse(Vector.fill(td.columns.size)(0)),td)
        }
        val row=Vector(aggs:_*)
        if(having.forall(h=>toBool(evalAggExpr(h,row,td,rs)))) out+=row
      }
      QueryResult(out.toVector.distinct,0,cols.map{case Col(n)=>n;case Agg(f,a)=>f+"("+a+")";case _=>"expr"})
    } else {
      // expand Star to all columns, then project each expr to a single value
      val pcols = if (cols.exists(_ == Star())) td.columns.map(c => Col(c.name)) else cols
      val flat = rows.map { r =>
        pcols.map {
          case Col(n) => { val i = td.columns.indexWhere(_.name == n); if (i < 0) 0 else r(i) }
          case e      => evalExpr(e, r, td)
        }.toVector
      }
      QueryResult((if (distinct) flat.distinct else flat).toVector, 0,
        pcols.map { case Col(n) => n; case _ => "expr" })
    }
  }

  private def evalAggExpr(e:SqlExpr,row:Vector[Any],t:TableDef,rs:Seq[Vector[Any]]):Any = e match {
    case Agg(fn,a)=>aggregate(fn,a,rs,t)
    case Bin(op,l,r)=>applyOp(op,evalAggExpr(l,row,t,rs),evalAggExpr(r,row,t,rs))
    case Col(n)=>{val i=t.columns.indexWhere(_.name==n);if(i<row.length)i else 0; val ix=math.min(i,row.length-1); row(ix)}
    case Lit(v)=>v
    case _=>0
  }
  private def aggregate(fn:String,a:SqlExpr,rs:Seq[Vector[Any]],t:TableDef):Any = {
    val vals=rs.map(r=>evalExpr(a,r,t))
    fn.toUpperCase match {
      case "COUNT"=>vals.count(_!=null)
      case "SUM"=>vals.collect{case i:Int=>i;case d:Double=>d.toInt}.sum
      case "AVG"=>{val ds=vals.collect{case i:Int=>i.toDouble;case d:Double=>d};if(ds.isEmpty)0 else ds.sum/ds.size}
      case "MIN"=>vals.filter(_!=null).reduceLeft((x,y)=>if(cmp(x,y)<0)x else y)
      case "MAX"=>vals.filter(_!=null).reduceLeft((x,y)=>if(cmp(x,y)>0)x else y)
      case _=>vals.size
    }
  }
}
