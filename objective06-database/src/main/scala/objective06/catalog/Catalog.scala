package objective06.catalog

/** Catalog: database metadata (O6).
  *
  * Column types are a small SQL subset. Tables own a heap file id and an
  * optional index list. The catalog itself is stored as a special table
  * (or serialized blob) via the storage layer.
  */
object Types {
  sealed trait ColType
  case object INT    extends ColType
  case object REAL   extends ColType
  case object TEXT   extends ColType
  case object BOOL   extends ColType
  def sizeOf(t: ColType): Int = t match {
    case INT | REAL => 4
    case BOOL       => 1
    case TEXT       => -1   // variable length
  }
}

case class Column(name: String, ctype: Types.ColType, nullable: Boolean = true)

case class TableDef(
  name: String,
  columns: Vector[Column],
  heapFileId: Int = -1,          // assigned by storage layer
  indexes: Vector[IndexDef] = Vector.empty
)

case class IndexDef(name: String, table: String, columns: Vector[String])

/** In-memory catalog with lookup + DDL operations. Persistence is layered
  * on top (serialize to a catalog page/blob via the heap/storage layer). */
class Catalog {
  private val tableMap = scala.collection.mutable.LinkedHashMap[String, TableDef]()
  private val dbs = scala.collection.mutable.LinkedHashMap[String, Unit]()

  def createDatabase(name: String): Unit = { dbs(name) = () }
  def hasDatabase(name: String): Boolean = dbs.contains(name)
  def databases: Iterable[String] = dbs.keys

  def createTable(t: TableDef): Unit = {
    require(!tableMap.contains(t.name), s"table ${t.name} exists")
    require(t.columns.nonEmpty, "table needs >=1 column")
    tableMap(t.name) = t
  }
  def dropTable(name: String): Unit = { tableMap.remove(name) }
  def table(name: String): Option[TableDef] = tableMap.get(name)
  def tables: Iterable[TableDef] = tableMap.values
  def tableNames: Iterable[String] = tableMap.keys

  def columnIndex(t: TableDef, col: String): Option[Int] =
    t.columns.indexWhere(_.name == col) match {
      case -1 => None; case i => Some(i)
    }
}
