package pubsub

import akka.NotUsed
import akka.actor.{ActorRef, StoppingSupervisorStrategy}
import akka.stream.scaladsl.{Sink, Source, SourceQueueWithComplete}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.testkit.TestProbe
import org.scalamock.scalatest.MockFactory
import org.scalatest.{GivenWhenThen, Matchers, SequentialNestedSuiteExecution}
import pubsub.EventStore.EventUpstream

import scala.concurrent.duration._
import scala.language.postfixOps

class SubscriptionActorTest extends BaseTestKit("SubscriptionActorTest",
  ActorSystemConfig()
    .withGuardianSupervisorStrategy[StoppingSupervisorStrategy]()) // we don't want actors under test to be restarted after failure
  with MockFactory
  with GivenWhenThen
  with Matchers
  with SequentialNestedSuiteExecution {

  /** Simulated event upstream created from a source of string */
  def eventUpstream(source: Source[String, NotUsed], startFrom: Long): EventUpstream = source
      .zipWithIndex
      .map { zip => EventNotification(ordinal = zip._2 + startFrom, payload = zip._1) }
      .runWith(Sink.asPublisher(fanout = false))

  /** Prematerializes an [[akka.stream.scaladsl.SourceQueueWithComplete[String]]] to
    * use it as a source for creating [[EventUpstream]] with [[eventUpstream()]] function.
    */
  def manualEventUpstream()(implicit materializer: ActorMaterializer): (SourceQueueWithComplete[String], Source[String, NotUsed]) = {
    val bufferSize = 1
    Source.queue[String](bufferSize, OverflowStrategy.fail).preMaterialize()
  }

  /** Subscriber actor. It will receive event notifications from Subscription. */
  val subscriberProbe = TestProbe("subscriberProbe")

  /** Subscription state watcher. It will receive notifications about state changes of the SubscriptionActor. */
  val stateWatcher = TestProbe("stateWatcher")

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val eventStoreMock: EventStore = mock[EventStore]

  /** SubscriptionActor under test.
    * We don't want to start it immediately. We just want to obtain an ActorRef to use it
    * later in the tests.
    */
  lazy val subscriptionRef: ActorRef = system.actorOf(
    SubscriptionActor.props(subscriberProbe.ref, "testTopic", 0, eventStoreMock, state => stateWatcher.ref ! state),
    "subscriptionActor")

  "A subscription actor" when {
    "started" must {
      "send subscription acknowledgement and catch up with event upstream" in {
        val (manualEventStream, source) = manualEventUpstream()
        (eventStoreMock.eventUpstream _).expects("testTopic", 0).returning(eventUpstream(source, 0))

        When(s"a SubscriptionActor ${subscriptionRef.path} is started")

        Then("it should send SubscribeAck to the subscriber")
        subscriberProbe.expectMsg(SubscribeAck(subscriptionRef))

        And("it should start catching up with event upstream")
        stateWatcher.expectMsg(Some("CatchingUpWithUpstream"))

        And("it should forward events from the upstream to the subscriber")
        manualEventStream.offer("event0")
        subscriberProbe.expectMsg(EventNotification(0, "event0"))

        manualEventStream.offer("event1")
        subscriberProbe.expectMsg(EventNotification(1, "event1"))

        And("it shouldn't accept and forward events from the topic to the subscriber")
        val someEventOrdinal = 3
        subscriptionRef ! EventNotification(someEventOrdinal, "event notification from topic")
        subscriberProbe.expectNoMessage(100 millis)

        And("it should start waiting for events when caught with event upstream")
        manualEventStream.complete()
        stateWatcher.expectMsg(Some("WaitingForEvents"))
      }
    }

    "waiting for events" must {
      "forward the event to the subscriber if it hasn't fallen behind the upstream" in {
        val eventNotification = EventNotification(2, "event3")
        subscriptionRef ! eventNotification
        subscriberProbe.expectMsg(eventNotification)
      }

      "start catching up with event upstream if it has fallen behind the upstream" in {
        val (_, source) = manualEventUpstream()
        (eventStoreMock.eventUpstream _).expects("testTopic", 3).returning(eventUpstream(source, 3))

        // imagine there was a network partition and we somehow skipped the events from 3 to 9
        val someEventOrdinal = 10
        subscriptionRef ! EventNotification(someEventOrdinal, "event10")

        subscriberProbe.expectNoMessage(100 millis)
        stateWatcher.expectMsg(Some("CatchingUpWithUpstream"))
      }
    }
  }
}
