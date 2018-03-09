package pubsub

import akka.actor.ActorRef
import akka.testkit.TestProbe
import org.reactivestreams.Subscriber
import org.scalatest.concurrent.Eventually
import org.scalatest.{Matchers, SequentialNestedSuiteExecution}
import pubsub.SubscriptionActor.EventUpstream

import scala.concurrent.duration._

class StubEventUpstream extends EventUpstream {
  var subscriber: Option[Subscriber[_ >: EventNotification]] = None
  override def subscribe(s: Subscriber[_ >: EventNotification]): Unit = { subscriber = Some(s) }

  def push(eventNotification: EventNotification): Unit = subscriber.foreach(_.onNext(eventNotification))
  def complete(): Unit = subscriber.foreach(_.onComplete())
}

class SubscriptionActorTest extends BaseTestKit("SubscriptionActorTest")
  with Matchers
  with Eventually
  with SequentialNestedSuiteExecution {

  val subscriberProbe = TestProbe("subscriberProbe")

  var currentState: Option[String] = _

  val eventUpstream = new StubEventUpstream()
  val subscriptionRef: ActorRef = system.actorOf(SubscriptionActor.props(subscriberProbe.ref, eventUpstream, state => currentState = state))

  "A subscription actor" when {
    "created" must {

      "send SubscribeAck to the subscriber and " in {
        subscriberProbe.expectMsg(SubscribeAck(subscriptionRef))
      }

      "start catching up with event upstream" in {
        currentState should be(Some("CatchingUpWithUpstream"))
      }
    }

    "catching up with upstream" must {

      "forward events from the upstream to the subscriber" in {
        eventUpstream.push(EventNotification("event1"))
        subscriberProbe.expectMsg(EventNotification("event1"))

        eventUpstream.push(EventNotification("event2"))
        subscriberProbe.expectMsg(EventNotification("event2"))
      }

      "not accept and forward events from topic to the subscriber" in {
        subscriptionRef ! EventNotification("event notification from topic")
        subscriberProbe.expectNoMessage(100 millis)
      }

      "start waiting for events when caught with event upstream" in {
        eventUpstream.complete()
        eventually { currentState should be (Some("WaitingForEvents")) }
      }
    }
  }
}
