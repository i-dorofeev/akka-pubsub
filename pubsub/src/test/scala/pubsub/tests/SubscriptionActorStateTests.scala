package pubsub.tests

import akka.actor.{Props, StoppingSupervisorStrategy}
import akka.stream.ActorMaterializer
import akka.testkit.TestProbe
import org.scalamock.scalatest.MockFactory
import pubsub.SubscriptionActor.EventOrdinal
import pubsub.fsm.StateFlow._
import pubsub.tests.fixtures.{EndStateFixture, EventStoreStubFixture}
import pubsub.tests.utils.{ActorSystemConfig, BaseTestKit}
import pubsub.{EventNotification, SubscribeAck, SubscriptionActor}

import scala.concurrent.duration._
import scala.language.postfixOps

//noinspection TypeAnnotation,ScalaStyle
class SubscriptionActorStateTests extends BaseTestKit("SubscriptionActorTest", ActorSystemConfig()
    .withGuardianSupervisorStrategy[StoppingSupervisorStrategy])
  with MockFactory
  with EventStoreStubFixture
  with EndStateFixture {

  class Fixture(val topic: String)(implicit val actorMaterializer: ActorMaterializer)
      extends EventStoreStub with EndState[EventOrdinal] {
    // This is a receiver of event notifications
    val subscriber = TestProbe("subscriber")
  }

  val waitDuration = 100 millis
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()

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

        eventSourceQueue.offer("event1")
        eventSourceQueue.offer("event2")
        actor ! EventNotification(4, "event4") // we shouldn't accept event notifications from topic in this state
        eventSourceQueue.offer("event3")
        eventSourceQueue.complete()

        subscriber.expectMsg(EventNotification(1L, "event1"))
        subscriber.expectMsg(EventNotification(2L, "event2"))
        subscriber.expectMsg(EventNotification(3L, "event3"))
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
