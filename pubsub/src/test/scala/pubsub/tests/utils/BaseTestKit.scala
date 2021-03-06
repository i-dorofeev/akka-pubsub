package pubsub.tests.utils

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.Config
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

/**
  * Base TestKit for testing actors.
  * @param actorSystemName Actor system name.
  */
class BaseTestKit(val actorSystemName: String, config: Option[Config] = None) extends TestKit(ActorSystem(actorSystemName, config))
  with WordSpecLike
  with BeforeAndAfterAll
  with ImplicitSender {

  /**
    * Shuts down actor system after all tests are finished, so you
    * don't have to bother to do it manually in every test suite.
    */
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
