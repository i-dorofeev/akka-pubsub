package pubsub

object BrokerDatabase extends BrokerDatabaseSchema {
  override val profile = slick.jdbc.H2Profile

  import profile.api._
  lazy val db: Database = Database.forConfig("pubsub.broker.db")
}
