import BrokerActor.{Event, Subscribe}
import akka.actor.{Actor, ActorLogging, RootActorPath}
import akka.cluster.Cluster
import akka.cluster.ClusterEvent._

class SubscriberActor extends Actor with ActorLogging {

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
      context.actorSelection(RootActorPath(member.address) / "user" / "broker") ! Subscribe("publisher", 0)

    case evt: Event =>
      log.info("Subscriber received {}", evt)

    case msg =>
      log.debug("Subscriber received {}", msg)
  }
}
