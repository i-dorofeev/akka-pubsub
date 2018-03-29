package pubsub.fixtures

import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.stream.scaladsl.{Sink, Source, SourceQueueWithComplete}
import org.scalamock.scalatest.MockFactory
import pubsub.{EventNotification, EventStore}
import pubsub.SubscriptionActor.EventOrdinal

/** Stubs an event store.
  *
  * Provides a queue to manually emit event notifications in controlled fashion in test environment.
  */
trait EventStoreStubFixture extends MockFactory {

  trait EventStoreStub {
    def topic: String

    implicit def actorMaterializer: ActorMaterializer

    // Manual event store stub
    val eventStoreStub: EventStore = stub[EventStore]

    val eventSourceQueue: SourceQueueWithComplete[String] =
      Source.queue[String](bufferSize = 1, OverflowStrategy.fail).preMaterialize() match {
        case (queue, source) =>

          // prepare manually controlled stub event stream for topic
          (eventStoreStub.eventUpstream _).when(topic, *).onCall { (_, startFrom: EventOrdinal) =>
            source
              .zipWithIndex
              .map { case (item, index) => EventNotification(ordinal = index + startFrom + 1, payload = item) }
              .runWith(Sink.asPublisher(fanout = false))
          }

          queue
      }
  }
}
