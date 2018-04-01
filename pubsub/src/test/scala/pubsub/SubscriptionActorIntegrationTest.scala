package pubsub

import akka.actor.{ActorRef, StoppingSupervisorStrategy}
import akka.stream.ActorMaterializer
import akka.testkit.TestProbe
import org.scalamock.scalatest.MockFactory
import org.scalatest.{GivenWhenThen, Matchers, SequentialNestedSuiteExecution}
import pubsub.fixtures.EventStoreStubFixture
import pubsub.utils.{ActorSystemConfig, BaseTestKit}

import scala.concurrent.duration._
import scala.language.postfixOps

//noinspection ScalaStyle
class SubscriptionActorIntegrationTest extends BaseTestKit("SubscriptionActorIntegrationTest",
  ActorSystemConfig()
    .withGuardianSupervisorStrategy[StoppingSupervisorStrategy]()) // we don't want actors under test to be restarted after failure
  with MockFactory
  with GivenWhenThen
  with Matchers
  with SequentialNestedSuiteExecution
  with EventStoreStubFixture {

  class Fixture(val topic: String)(implicit val actorMaterializer: ActorMaterializer)
      extends EventStoreStub {

    /** Subscriber actor. It will receive event notifications from Subscription. */
    val subscriberProbe = TestProbe("subscriberProbe")

    /** Subscription state watcher. It will receive notifications about state changes of the SubscriptionActor. */
    val stateWatcher = TestProbe("stateWatcher")
  }

  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()

  "A SubscriptionActor integration test" in new Fixture("testTopic") {
    When("a SubscriptionActor is started")
    val subscriptionRef: ActorRef = system.actorOf(
      SubscriptionActor.props(subscriberProbe.ref, topic, 0, eventStoreStub, state => stateWatcher.ref ! state),
      "subscriptionActor")

    Then("it should send SubscribeAck to the subscriber")
    subscriberProbe.expectMsg(SubscribeAck(subscriptionRef))

    And("it should start catching up with event upstream")
    stateWatcher.expectMsg(Some("CatchingUpWithUpstream"))

    When("it is catching up with event upstream")
    eventSourceQueue.offer("event0")
    eventSourceQueue.offer("event1")

    Then("it should forward events from the upstream to the subscriber")
    subscriberProbe.expectMsg(EventNotification(0, "event0"))
    subscriberProbe.expectMsg(EventNotification(1, "event1"))

    And("it shouldn't accept and forward events from the topic to the subscriber")
    subscriptionRef ! EventNotification(3, "event notification from topic")
    subscriberProbe.expectNoMessage(100 millis)

    When("it has caught up with event upstream")
    eventSourceQueue.complete()

    Then("it should start waiting for events from topic")
    stateWatcher.expectMsg(Some("WaitingForEvents"))

    And("forward them to the subscriber")
    val eventNotification = EventNotification(2, "event2")
    subscriptionRef ! eventNotification
    subscriberProbe.expectMsg(eventNotification)

    When("it receives an event with unexpectedly greater ordinal")
    // imagine there was a network partition and we somehow skipped the events from 3 to 9
    subscriptionRef ! EventNotification(10, "event10")

    Then("it shouldn't accept and forward the event")
    subscriberProbe.expectNoMessage(100 millis)

    And("it should start catching up with event upstream to fetch the missed events")
    stateWatcher.expectMsg(Some("CatchingUpWithUpstream"))
  }
}
