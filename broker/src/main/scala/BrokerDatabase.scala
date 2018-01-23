import slick.jdbc.H2Profile.api._
import slick.migration.api.TableMigration

import scala.concurrent.Future

class Events(tag: Tag) extends Table[(Int, String, String)](tag, "events") {
  def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
  def topic = column[String]("topic")
  def payload = column[String]("payload")

  def * = (id, topic, payload)
}


object BrokerDatabase {

  val events = TableQuery[Events]

  /*
  val migration = TableMigration(events)
    .create
    .addColumns(_.id, _.topic, _.payload)
    */

  val db = Database.forConfig("brokerDb")

  def initialize(): Future[Unit] = {
    val setup = DBIO.seq(
      events.schema.create
    )

    db.run(setup)
  }

  def persistEvent(evt: Event): Future[Unit] = {
    val insert = DBIO.seq(
      events += (0, evt.topic, evt.payload)
    )

    db.run(insert)
  }
}
