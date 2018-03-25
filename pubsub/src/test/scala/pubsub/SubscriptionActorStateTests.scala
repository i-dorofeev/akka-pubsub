package pubsub

import akka.actor.{Props, StoppingSupervisorStrategy}
import akka.testkit.TestProbe
import org.scalamock.scalatest.MockFactory
import pubsub.SubscriptionActor.EventOrdinal
import pubsub.fsm.{FSMActorState, Leave, StateFlow}
import StateFlow._
import akka.NotUsed
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source, SourceQueueWithComplete}
import pubsub.EventStore.EventUpstream

import scala.concurrent.duration._
import scala.language.postfixOps

//noinspection TypeAnnotation,ScalaStyle
class SubscriptionActorStateTests extends BaseTestKit("SubscriptionActorTest", ActorSystemConfig()
    .withGuardianSupervisorStrategy[StoppingSupervisorStrategy])
  with MockFactory {

  trait Fixture {
    val waitDuration = 100 millis

    val subscriber = TestProbe("subscriber")
    val endState = endStateStub[EventOrdinal]()
    val topic = "testTopic"

    def endStateStub[T](): FSMActorState[T] = {
      val endState = stub[FSMActorState[T]]
      (endState.onEnter _).when(*).onCall { i: T => Leave(i) }
      (endState.name _).when().returning("endState")
      endState
    }

    implicit val materializer: ActorMaterializer = ActorMaterializer()

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

    val eventStoreStub: EventStore = stub[EventStore]
    val (manualEventStream, source) = manualEventUpstream()
    (eventStoreStub.eventUpstream _).when(topic, *).onCall { (_, i: EventOrdinal) => eventUpstream(source, i + 1) }

    def verifyState(expectedState: EventOrdinal) = (endState.onEnter _).verify(expectedState)
  }

  "A SubscriptionActor" when {
    "created" should {
      "send SubscriptionAck and leave" in new Fixture {
        val subscriptionActorProps = Props(new SubscriptionActor(subscriber.ref, topic, 1, eventStoreStub) {
          override val stateFlow = Created >>: endState
        })

        val actor = watch (system.actorOf(subscriptionActorProps, "subscriptionActor"))

        subscriber.expectMsg(SubscribeAck(actor))
        subscriber.expectNoMessage(waitDuration)

        expectTerminated(actor)

        verifyState(1L)
      }
    }

    "catching up with event upstream" should {
      "accept events from upstream and forward them to the subscriber" in new Fixture {
        val subscriptionActorProps = Props(new SubscriptionActor(subscriber.ref, topic, 1, eventStoreStub) {
          override val stateFlow = CatchingUpWithUpstream >>: endState
        })

        val actor = watch (system.actorOf(subscriptionActorProps, "subscriptionActor"))

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

        verifyState(4L)
      }
    }
  }
}
