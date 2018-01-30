package pubsub

import com.typesafe.config.ConfigFactory
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

object BrokerDatabase extends BrokerDatabaseSchema {

  private val config = ConfigFactory.load()
  private val dbConfig = config.getConfig("pubsub.broker")
  private val databaseConfig = DatabaseConfig.forConfig[JdbcProfile]("slick", dbConfig)

  override val profile = databaseConfig.profile
  override val db = databaseConfig.db
}
