package pubsub.old

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, Ignore, Matchers, WordSpecLike}
import pubsub.old.BrokerActor.{Event, ServiceUnavailable}

@Ignore
class BrokerDatabaseSelfHealingTest extends TestKit(ActorSystem("BrokerDatabaseSelfHealingTest"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  val slickConfig: Config = ConfigFactory.parseString(
    """
      pubsub.broker.slick {
        profile = "slick.jdbc.H2Profile$"
        db {
          url = "jdbc:h2:tcp://localhost/~/broker"
          driver = org.h2.Driver
          connectionPool = disabled
          keepAliveConnection = true
        }
      }
    """.stripMargin)

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  private val broker = system.actorOf(BrokerActor.props(slickConfig))

  "A broker actor" must {
    "respond with ServiceUnavailable if database is unavailable" in {
      broker ! Event("topic1", 0, "payload")
      expectMsg(ServiceUnavailable)
    }
  }

}
