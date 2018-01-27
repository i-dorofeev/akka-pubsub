import BrokerActor.{Event, Subscribe}
import SubscriptionActor.SubscriptionAck
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}

import scala.concurrent.duration._

class BrokerTest() extends TestKit(ActorSystem("BrokerTest"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
    println("Actor system shut down.")
  }

  private val broker = system.actorOf(BrokerActor.props())

  "A broker actor" must {
    "not deliver an event if not subscribed" in {
      broker ! Event("topic1", 0, "payload")
      expectNoMessage(100 millis)
    }

    "deliver an event if subscribed" in {
      val subscriber = TestProbe()
      subscriber.send(broker, Subscribe("topic2", 0))
      subscriber.expectMsgClass(classOf[SubscriptionAck])

      broker ! Event("topic2", 0, "topic2 - 0")

      subscriber.expectMsg(Event("topic2", 0, "topic2 - 0"))
    }

    "deliver all the events available from requested id on subscription" in {
      broker ! Event("topic3", 0, "topic3 - 0")
      broker ! Event("topic3", 1, "topic3 - 1")
      broker ! Event("topic3", 2, "topic3 - 2")

      val subscriber = TestProbe()
      subscriber.send(broker, Subscribe("topic3", 1))
      subscriber.expectMsgClass(classOf[SubscriptionAck])
      subscriber.expectMsg(Event("topic3", 1, "topic3 - 1"))
      subscriber.expectMsg(Event("topic3", 2, "topic3 - 2"))
    }
  }

}
