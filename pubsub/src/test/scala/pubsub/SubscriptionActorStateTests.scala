package pubsub

import akka.actor.{Props, StoppingSupervisorStrategy}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.testkit.TestProbe
import org.scalamock.scalatest.MockFactory
import pubsub.SubscriptionActor.EventOrdinal
import pubsub.fsm.StateFlow._
import pubsub.fsm.{FSMActorState, Leave}

import scala.concurrent.duration._
import scala.language.postfixOps

//noinspection TypeAnnotation,ScalaStyle
class SubscriptionActorStateTests extends BaseTestKit("SubscriptionActorTest", ActorSystemConfig()
    .withGuardianSupervisorStrategy[StoppingSupervisorStrategy])
  with MockFactory {

  trait Fixture {
    val waitDuration = 100 millis

    // This is a receiver of event notifications
    val subscriber = TestProbe("subscriber")

    // End state receives the state value from the state under test and ends the state flow
    val endState = stub[FSMActorState[EventOrdinal]]
    (endState.onEnter _).when(*).onCall { i: EventOrdinal => Leave(i) }
    (endState.name _).when().returning("endState")

    /** Verifies the state value after leaving the state under test.
      *
      * @param expectedStateValue Expected state value.
      */
    def verifyStateValue(expectedStateValue: EventOrdinal) = (endState.onEnter _).verify(expectedStateValue)

    val topic = "testTopic"

    // Manual event store stub
    val eventStoreStub: EventStore = stub[EventStore]

    implicit val materializer: ActorMaterializer = ActorMaterializer()
    val (manualEventStream, source) = Source.queue[String](bufferSize = 1, OverflowStrategy.fail).preMaterialize()
    (eventStoreStub.eventUpstream _).when(topic, *).onCall { (_, startFrom: EventOrdinal) =>
      source
        .zipWithIndex
        .map { zip => EventNotification(ordinal = zip._2 + startFrom + 1, payload = zip._1) }
        .runWith(Sink.asPublisher(fanout = false)) }
  }

  "A SubscriptionActor" when {
    "created" should {
      "send SubscriptionAck and leave" in new Fixture {
        val subscriptionActorProps = Props(new SubscriptionActor(subscriber.ref, topic, 1, eventStoreStub) {
          override val stateFlow = Created >>: endState
        })

        val actor = watch (system.actorOf(subscriptionActorProps))

        subscriber.expectMsg(SubscribeAck(actor))
        subscriber.expectNoMessage(waitDuration)

        expectTerminated(actor)

        verifyStateValue(1L)
      }
    }

    "catching up with event upstream" should {
      "accept events from upstream and forward them to the subscriber" in new Fixture {
        val subscriptionActorProps = Props(new SubscriptionActor(subscriber.ref, topic, 1, eventStoreStub) {
          override val stateFlow = CatchingUpWithUpstream >>: endState
        })

        val actor = watch (system.actorOf(subscriptionActorProps))

        manualEventStream.offer("event2")
        manualEventStream.offer("event3")
        actor ! EventNotification(5, "event5") // we shouldn't accept event notifications from topic in this state
        manualEventStream.offer("event4")
        manualEventStream.complete()

        subscriber.expectMsg(EventNotification(2L, "event2"))
        subscriber.expectMsg(EventNotification(3L, "event3"))
        subscriber.expectMsg(EventNotification(4L, "event4"))
        subscriber.expectNoMessage(waitDuration)

        expectTerminated(actor)

        (eventStoreStub.eventUpstream _).verify(topic, 1)
        verifyStateValue(4L)
      }
    }

    "waiting for event notifications" should {
      "forward event notifications from topic to subscriber" in new Fixture {
        val subscriptionActorProps = Props(new SubscriptionActor(subscriber.ref, topic, 1, eventStoreStub) {
          override val stateFlow = WaitingForEvents >>: endState
        })

        val actor = watch (system.actorOf(subscriptionActorProps))

        actor ! EventNotification(1, "event1")
        actor ! EventNotification(2, "event2")
        // simulate that event3 is lost for any reason
        actor ! EventNotification(4, "event4")

        subscriber.expectMsg(EventNotification(1, "event1"))
        subscriber.expectMsg(EventNotification(2, "event2"))
        subscriber.expectNoMessage(waitDuration)

        expectTerminated(actor)

        verifyStateValue(3L)
      }
    }
  }
}
