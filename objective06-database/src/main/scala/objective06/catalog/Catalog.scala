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

  // ---- persistence: serialize catalog to/from a byte blob ----
  // Format: [ndbs u32][for each db: namelen u16+name][ntables u32][per table:
  //   namelen+name, ncols u32, per col: namelen+name, typetag u8, then heapFileId i32]
  def serialize(): Array[Byte] = {
    val out = scala.collection.mutable.ArrayBuffer[Byte]()
    def put32(v:Int)= { val b=Array.ofDim[Byte](4); java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.BIG_ENDIAN).putInt(v); out++=b }
    def put16(v:Int)= { val b=Array.ofDim[Byte](2); java.nio.ByteBuffer.wrap(b).order(java.nio.ByteOrder.BIG_ENDIAN).putShort(v.toShort); out++=b }
    def putStr(s:String)= { val b=s.getBytes("UTF-8"); put16(b.length); out++=b }
    def putType(t:Types.ColType)= out+= (t match {case Types.INT=>1;case Types.REAL=>2;case Types.TEXT=>3;case Types.BOOL=>4})
    put32(dbs.size); dbs.keys.foreach(putStr)
    put32(tableMap.size)
    tableMap.values.foreach{ t =>
      putStr(t.name); put32(t.columns.size)
      t.columns.foreach{ c => putStr(c.name); putType(c.ctype) }
      put32(t.heapFileId)
    }
    out.toArray
  }

  def deserialize(data: Array[Byte]): Unit = {
    tableMap.clear(); dbs.clear()
    val buf = java.nio.ByteBuffer.wrap(data).order(java.nio.ByteOrder.BIG_ENDIAN)
    def g32()=buf.getInt; def g16()=buf.getShort&0xffff
    def gstr()= { val l=g16(); val b=Array.ofDim[Byte](l); buf.get(b); new String(b,"UTF-8") }
    def gtype()= buf.get().toInt match {case 1=>Types.INT;case 2=>Types.REAL;case 3=>Types.TEXT;case _=>Types.BOOL}
    val ndb=g32(); var i=0; while(i<ndb){dbs(gstr())=();i+=1}
    val nt=g32(); i=0; while(i<nt){
      val n=gstr(); val nc=g32(); val cols=Vector.fill(nc){ val cn=gstr(); Column(cn,gtype()) }
      val hf=g32(); tableMap(n)=TableDef(n,cols,heapFileId=hf); i+=1
    }
  }
}
