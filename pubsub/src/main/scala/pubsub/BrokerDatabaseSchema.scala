package pubsub

import pubsub.BrokerActor.Event
import slick.basic.DatabasePublisher
import slick.jdbc.JdbcProfile

import scala.concurrent.Future

trait BrokerDatabaseSchema {

  val profile: JdbcProfile

  import profile.api._
  val db: Database

  class Events(tag: Tag) extends Table[(Int, String, Int, String)](tag, "events") {
    def id = column[Int]("id", O.PrimaryKey, O.AutoInc)
    def topic = column[String]("topic")
    def eventId = column[Int]("event_id")
    def payload = column[String]("payload")

    def * = (id, topic, eventId, payload)
  }

  val events = TableQuery[Events]

  def initialize(): Future[Unit] = {
    val setup = DBIO.seq(
      events.schema.create
    )

    db.run(setup)
  }

  def persistEvent(evt: Event): Future[Unit] = {
    val insert = DBIO.seq(
      events += (0, evt.topic, evt.eventId, evt.payload)
    )

    db.run(insert)
  }

  def fetchEvents(topic: String, fromEventId: Int): DatabasePublisher[Event] = {
    val query = events
        .filter(_.topic === topic)
        .filter(_.eventId >= fromEventId)
        .sortBy(_.eventId)
    db.stream(query.result)
        .mapResult { case (_, eventTopic, eventId, payload) => Event(eventTopic, eventId, payload) }
  }

  def shutdown(): Unit = {
    db.close()
  }
}