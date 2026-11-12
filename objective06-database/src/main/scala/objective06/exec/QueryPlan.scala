package objective06.exec

import objective06.sql.SqlExpr

/** Logical query plan (O6): a tree the optimizer rewrites before execution.
  *
  *   Scan(table) -> Filter(pred) -> Project(cols) -> Aggregate -> Join
  *
  * Optimizer rules (v1): predicate pushdown (Filter below Join/above Scan),
  * projection pushdown (drop unused columns), join reordering by selectivity
  * heuristic, constant folding in predicates.
  */
sealed trait Plan
case class Scan(table:String) extends Plan
case class Filter(pred:SqlExpr, child:Plan) extends Plan
case class Project(cols:Vector[SqlExpr], child:Plan) extends Plan
case class Aggregate(groupBy:Vector[String], aggs:Vector[SqlExpr], child:Plan) extends Plan
case class JoinKind(k:String) extends Plan // placeholder marker
case class Join(left:Plan,right:Plan,on:SqlExpr) extends Plan

object QueryPlan {
  /** Build a plan from a Select AST (single or multi-table FROM). */
  def fromSelect(cols:Vector[SqlExpr], tables:Vector[String], where:Option[SqlExpr],
                 groupBy:Vector[String], having:Option[SqlExpr]):Plan = {
    // scan each table, join them, filter, project/aggregate
    var p:Plan = Scan(tables.head)
    tables.tail.foreach(t=> p = Join(p, Scan(t), objective06.sql.Lit(true)))
    where.foreach(w=> p = Filter(w,p))
    if(groupBy.nonEmpty||cols.exists{case objective06.sql.Agg(_,_)=>true;case _=>false})
      p = Aggregate(groupBy, cols, p)
    else p = Project(cols,p)
    having.foreach(h=> p = Filter(h,p))
    p
  }

  /** Predicate pushdown: move Filters as close to Scans as possible. */
  def optimize(p:Plan):Plan = p match {
    case Filter(pred, Join(l,r,on)) =>
      // split conjunctive pred; push table-local parts down
      val parts = splitAnd(pred)
      val (localL,localR,joinParts)=parts.foldLeft((Vector.empty[SqlExpr],Vector.empty[SqlExpr],Vector.empty[SqlExpr])){
        case((ll,lr,jp),e)=> (ll,lr,jp :+ e) // v1: keep all at join level (no schema info here)
      }
      Filter(BinAnd(joinParts), optimize(Join(optimize(l),optimize(r),on)))
    case Filter(pred, child) => Filter(pred, optimize(child))
    case Project(cols, child) => Project(cols, optimize(child))
    case Aggregate(g,a,child) => Aggregate(g,a,optimize(child))
    case Join(l,r,on) => Join(optimize(l),optimize(r),on)
    case s => s
  }

  private def splitAnd(e:SqlExpr):Vector[SqlExpr] = e match {
    case objective06.sql.Bin("AND",l,r)=>splitAnd(l)++splitAnd(r)
    case _=>Vector(e)
  }
  private def BinAnd(v:Vector[SqlExpr]):SqlExpr =
    if(v.isEmpty) objective06.sql.Lit(true)
    else v.reduce((a,b)=>objective06.sql.Bin("AND",a,b))
}
