package objective06

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import objective06.buffer.MemoryPageStore
import objective06.exec.Database
import objective06.sql.Sql

class DatabaseSpec extends AnyFlatSpec with Matchers {

  "Database" should "create table, insert, and select" in {
    val db = new Database(new MemoryPageStore(512))
    db.exec(Sql.parse("CREATE TABLE users (id INT, name TEXT, age INT)"))
    db.exec(Sql.parse("INSERT INTO users VALUES (1, 'alice', 30)"))
    db.exec(Sql.parse("INSERT INTO users VALUES (2, 'bob', 25)"))
    db.exec(Sql.parse("INSERT INTO users VALUES (3, 'carol', 35)"))

    val r = db.exec(Sql.parse("SELECT id, name FROM users WHERE age > 26"))
    r.rows should have size 2
    r.rows.map(_(0)) should contain theSameElementsAs Seq(1, 3)
  }

  it should "support aggregation" in {
    val db = new Database(new MemoryPageStore(512))
    db.exec(Sql.parse("CREATE TABLE t (x INT)"))
    db.exec(Sql.parse("INSERT INTO t VALUES (10)"))
    db.exec(Sql.parse("INSERT INTO t VALUES (20)"))
    db.exec(Sql.parse("INSERT INTO t VALUES (30)"))
    val r = db.exec(Sql.parse("SELECT COUNT(x), SUM(x), AVG(x) FROM t"))
    r.rows should have size 1
    r.rows(0)(0) shouldBe 3
    r.rows(0)(1) shouldBe 60
  }

  it should "select star" in {
    val db = new Database(new MemoryPageStore(512))
    db.exec(Sql.parse("CREATE TABLE t (a INT, b TEXT)"))
    db.exec(Sql.parse("INSERT INTO t VALUES (7, 'hi')"))
    val r = db.exec(Sql.parse("SELECT * FROM t"))
    r.rows should have size 1
    r.rows(0).toSeq should contain theSameElementsAs Seq(7, "hi")
  }

  it should "join two tables" in {
    val db = new Database(new MemoryPageStore(512))
    db.exec(Sql.parse("CREATE TABLE a (id INT, x INT)"))
    db.exec(Sql.parse("CREATE TABLE b (id INT, y INT)"))
    db.exec(Sql.parse("INSERT INTO a VALUES (1, 10)"))
    db.exec(Sql.parse("INSERT INTO a VALUES (2, 20)"))
    db.exec(Sql.parse("INSERT INTO b VALUES (1, 100)"))
    db.exec(Sql.parse("INSERT INTO b VALUES (2, 200)"))
    val r = db.exec(Sql.parse("SELECT a.id, x, y FROM a JOIN b ON a.id = b.id"))
    r.rows should have size 2
    r.rows.map(_(0)).toSet shouldBe Set(1, 2)
  }

  it should "cross join via comma" in {
    val db = new Database(new MemoryPageStore(512))
    db.exec(Sql.parse("CREATE TABLE a (v INT)"))
    db.exec(Sql.parse("CREATE TABLE b (w INT)"))
    db.exec(Sql.parse("INSERT INTO a VALUES (1)"))
    db.exec(Sql.parse("INSERT INTO a VALUES (2)"))
    db.exec(Sql.parse("INSERT INTO b VALUES (9)"))
    val r = db.exec(Sql.parse("SELECT v, w FROM a, b"))
    r.rows should have size 2
  }
}
