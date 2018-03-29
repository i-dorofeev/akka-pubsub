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

  implicit val materializer: ActorMaterializer = ActorMaterializer()

  /** Stubs an event store.
    *
    * Provides a queue to manually emit event notifications in controlled fashion in test environment.
    */
  trait EventStoreStubFixture {
    def topic: String

    // Manual event store stub
    val eventStoreStub: EventStore = stub[EventStore]

    val eventSourceQueue = Source.queue[String](bufferSize = 1, OverflowStrategy.fail).preMaterialize() match {
      case (queue, source) =>

        // prepare manually controlled stub event stream for topic
        (eventStoreStub.eventUpstream _).when(topic, *).onCall { (_, startFrom: EventOrdinal) =>
          source
            .zipWithIndex
            .map { case (item, index) => EventNotification(ordinal = index + startFrom + 1, payload = item) }
            .runWith(Sink.asPublisher(fanout = false)) }

        queue
    }
  }

  trait EndStateFixture {

    // End state receives the state value from the state under test and ends the state flow
    val EndState = stub[FSMActorState[EventOrdinal]]
    (EndState.onEnter _).when(*).onCall { i: EventOrdinal => Leave(i) }
    (EndState.name _).when().returning("endState")

    /** Verifies the state value after leaving the state under test.
      *
      * @param expectedStateValue Expected state value.
      */
    def verifyStateValue(expectedStateValue: EventOrdinal) = (EndState.onEnter _).verify(expectedStateValue)
  }

  class Fixture(val topic: String) extends EventStoreStubFixture with EndStateFixture {
    // This is a receiver of event notifications
    val subscriber = TestProbe("subscriber")
  }

  val waitDuration = 100 millis

  "A SubscriptionActor" when {
    "created" should {
      "send SubscriptionAck and leave" in new Fixture("testTopic") {
        val subscriptionActorProps = Props(new SubscriptionActor(subscriber.ref, topic, 1, eventStoreStub) {
          override val stateFlow = Created >>: EndState
        })

        val actor = watch (system.actorOf(subscriptionActorProps))

        subscriber.expectMsg(SubscribeAck(actor))
        subscriber.expectNoMessage(waitDuration)

        expectTerminated(actor)

        verifyStateValue(1L)
      }
    }

    "catching up with event upstream" should {
      "accept events from upstream and forward them to the subscriber" in new Fixture("testTopic") {
        val subscriptionActorProps = Props(new SubscriptionActor(subscriber.ref, topic, 1, eventStoreStub) {
          override val stateFlow = CatchingUpWithUpstream >>: EndState
        })

        val actor = watch (system.actorOf(subscriptionActorProps))

        eventSourceQueue.offer("event2")
        eventSourceQueue.offer("event3")
        actor ! EventNotification(5, "event5") // we shouldn't accept event notifications from topic in this state
        eventSourceQueue.offer("event4")
        eventSourceQueue.complete()

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
      "forward event notifications from topic to subscriber" in new Fixture("testTopic") {
        val subscriptionActorProps = Props(new SubscriptionActor(subscriber.ref, topic, 1, eventStoreStub) {
          override val stateFlow = WaitingForEvents >>: EndState
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
