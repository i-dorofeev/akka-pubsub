package pubsub

import org.reactivestreams.Publisher
import pubsub.EventStore.EventUpstream

trait EventStore {
  def eventUpstream(topic: String, startFrom: Long): EventUpstream
}

object EventStore {
  type EventUpstream = Publisher[EventNotification]
}

