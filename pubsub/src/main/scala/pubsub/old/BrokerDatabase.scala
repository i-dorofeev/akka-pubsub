package pubsub.old

import com.typesafe.config.Config
import slick.basic.DatabaseConfig
import slick.jdbc.JdbcProfile

class BrokerDatabase(val config: Config) extends BrokerDatabaseSchema {

  private val dbConfig = config.getConfig("pubsub.broker")
  private val databaseConfig = DatabaseConfig.forConfig[JdbcProfile]("slick", dbConfig)

  override val profile = databaseConfig.profile
  override val db = databaseConfig.db
}
