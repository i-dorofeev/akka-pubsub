import akka.actor.{Actor, RootActorPath}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._

class SubscriberActor extends Actor {

  val cluster = Cluster(context.system)

  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsEvents,
      classOf[ClusterDomainEvent])
  }

  override def postStop(): Unit = {
    cluster.unsubscribe(self)
  }

  override def receive: Receive = {
    case MemberUp(member) =>
      context.actorSelection(RootActorPath(member.address) / "user" / "broker") ! Subscribe("publisher")

    case Event(topic, payload) =>
      println(s"Subscriber received event($topic - $payload)")

    case evt =>
      println(s"SubscriberActor received: $evt")
  }
}
