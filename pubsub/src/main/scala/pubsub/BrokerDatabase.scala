package pubsub

import com.typesafe.config.ConfigFactory

object BrokerDatabase extends BrokerDatabaseSchema {

  private val config = ConfigFactory.load()
  private val dbConfig = config.getConfig("pubsub.broker.db")
  private val driver = dbConfig.getString("driver")

  override val profile = {
    driver match {
      case "org.h2.Driver" => slick.jdbc.H2Profile
    }
  }

  import profile.api.Database

  override lazy val db: Database = Database.forConfig("", dbConfig)
}
