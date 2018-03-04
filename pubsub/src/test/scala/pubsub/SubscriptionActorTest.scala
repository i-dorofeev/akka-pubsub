package pubsub

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.reactivestreams.Subscriber
import org.scalatest.{BeforeAndAfterAll, Matchers, SequentialNestedSuiteExecution, WordSpecLike}
import pubsub.SubscriptionActor.EventUpstream

import scala.concurrent.duration._

class StubEventUpstream extends EventUpstream {
  var subscriber: Option[Subscriber[_ >: EventNotification]] = None
  override def subscribe(s: Subscriber[_ >: EventNotification]): Unit = { subscriber = Some(s) }

  def push(eventNotification: EventNotification): Unit = subscriber.foreach(_.onNext(eventNotification))
  def complete(): Unit = subscriber.foreach(_.onComplete())
}

class SubscriptionActorTest extends TestKit(ActorSystem("SubscriptionActorTest"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with SequentialNestedSuiteExecution {

  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }

  val subscriberProbe = TestProbe("subscriberProbe")

  var subscriptionRef: ActorRef = _

  "A subscription actor" when {
    "created" must {
      val eventUpstream = new StubEventUpstream()

      "init" in {
        subscriptionRef = system.actorOf(SubscriptionActor.props(subscriberProbe.ref, eventUpstream))
      }

      "send SubscribeAck to the subscriber" in {
        subscriberProbe.expectMsg(SubscribeAck(subscriptionRef))
      }

      "catch up with event upstream" in {
        eventUpstream.push(EventNotification("event1"))
        subscriberProbe.expectMsg(EventNotification("event1"))

        eventUpstream.push(EventNotification("event2"))
        subscriberProbe.expectMsg(EventNotification("event2"))
      }

      "not forward events from topic to the subscriber" in {
        subscriptionRef ! EventNotification("event notification from topic")
        subscriberProbe.expectNoMessage(100 millis)
      }

      "exitState" in {
        eventUpstream.complete()
      }
    }
  }
}
