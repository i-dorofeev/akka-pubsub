import akka.actor.{ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.time.Milliseconds
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._

class BrokerTest() extends TestKit(ActorSystem("BrokerTest"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A broker actor" must {
    "not deliver an event if not subscribed" in {
      val broker = system.actorOf(Props(classOf[BrokerActor]))
      broker ! Event("test-topic", 0, "payload")
      expectNoMessage(100 millis)
    }
  }

}
