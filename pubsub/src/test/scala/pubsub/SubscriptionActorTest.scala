package pubsub

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source, SourceQueueWithComplete}
import akka.testkit.TestProbe
import org.scalatest.{Matchers, SequentialNestedSuiteExecution}
import pubsub.EventStore.EventUpstream

import scala.concurrent.duration._
import scala.language.postfixOps

class StubEventStore(val sourceByTopic: PartialFunction[String, Source[String, NotUsed]])(implicit materializer: Materializer) extends EventStore {

  override def eventUpstream(topic: String, startFrom: Long): EventUpstream = sourceByTopic(topic)
      .zipWithIndex
      .map { zip => EventNotification(ordinal = zip._2 + startFrom, payload = zip._1) }
      .runWith(Sink.asPublisher(fanout = false))
}

class SubscriptionActorTest extends BaseTestKit("SubscriptionActorTest")
  with Matchers
  with SequentialNestedSuiteExecution {

  val subscriberProbe = TestProbe("subscriberProbe")
  val stateWatcher = TestProbe("stateWatcher")

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  var manualEventUpstream: SourceQueueWithComplete[String] = _

  val eventStore = new StubEventStore({
    case "testTopic" =>
      val bufferSize = 1
      val (queue, source) = Source.queue[String](bufferSize, OverflowStrategy.fail).preMaterialize()
      manualEventUpstream = queue
      source
  })

  val subscriptionRef: ActorRef = system.actorOf(
    SubscriptionActor.props(subscriberProbe.ref, "testTopic", 0, eventStore, state => stateWatcher.ref ! state))

  "A subscription actor" when {
    "created" must {

      "send SubscribeAck to the subscriber and " in {
        subscriberProbe.expectMsg(SubscribeAck(subscriptionRef))
      }

      "start catching up with event upstream" in {
        stateWatcher.expectMsg(Some("CatchingUpWithUpstream"))
      }
    }

    "catching up with upstream" must {

      "forward events from the upstream to the subscriber" in {
        manualEventUpstream.offer("event0")
        subscriberProbe.expectMsg(EventNotification(0, "event0"))

        manualEventUpstream.offer("event1")
        subscriberProbe.expectMsg(EventNotification(1, "event1"))
      }

      "not accept and forward events from topic to the subscriber" in {
        val someEventOrdinal = 3
        subscriptionRef ! EventNotification(someEventOrdinal, "event notification from topic")
        subscriberProbe.expectNoMessage(100 millis)
      }

      "start waiting for events when caught with event upstream" in {
        manualEventUpstream.complete()
        stateWatcher.expectMsg(Some("WaitingForEvents"))
      }
    }

    "waiting for events" must {
      "forward the event to the subscriber if it hasn't fallen behind the upstream" in {
        val eventNotification = EventNotification(3, "event3")
        subscriptionRef ! eventNotification
        subscriberProbe.expectMsg(eventNotification)
      }

      "start catching up with event upstream if it has fallen behind the upstream" in {
        val someEventOrdinal = 10
        subscriptionRef ! EventNotification(someEventOrdinal, "event10")

        subscriberProbe.expectNoMessage(100 millis)
        stateWatcher.expectMsg(Some("CatchingUpWithUpstream"))
      }
    }
  }
}
