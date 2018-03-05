package pubsub

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit}
import org.scalatest.{BeforeAndAfterAll, Matchers, SequentialNestedSuiteExecution, WordSpecLike}
import pubsub.fsm.FSMActorState.actionState
import pubsub.fsm._

import scala.collection.mutable

class FSMActorTest extends TestKit(ActorSystem("FSMActorTest"))
  with ImplicitSender
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with SequentialNestedSuiteExecution {

  var fsmTestActor: ActorRef = _

  "An FSMTestActor" must {
    "go through State1 and State2 to State3" in {
      fsmTestActor = system.actorOf(Props(new FSMTestActor(self)))

      expectMsg("state1.onEnter")
      expectMsg("state2.onEnter")
      expectMsg("state3.onEnter")
    }

    "handle messages by State3 receiver" in {
      fsmTestActor ! "stay in State3"
      expectMsg("staying in State3")
    }

    "handle unknown messages without leaving current State3" in {
      fsmTestActor ! "unknown message"
      fsmTestActor ! "stay in State3"
      expectMsg("staying in State3")
    }

    "leave State3 after receiving 'leave' message and enter State4" in {
      fsmTestActor ! "leave State3"
      expectMsg("leaving State3")
      expectMsg("state3.onExit")
    }
  }

}

class FSMTestActor(watcher: ActorRef) extends FSMActor {

  val State1: FSMActorState = actionState { () => watcher ! "state1.onEnter" }

  val State2: FSMActorState = new FSMActorState {
    override def onEnter(): StateActionResult = {
      watcher ! "state2.onEnter"
      Leave
    }
  }

  val State3: FSMActorState = new FSMActorState {
    override def onEnter(): StateActionResult = {
      watcher ! "state3.onEnter"
      Stay
    }

    override def receive: PartialFunction[Any, StateActionResult] = {
      case "stay in State3" =>
        watcher ! "staying in State3"
        Stay

      case "leave State3" =>
        watcher ! "leaving State3"
        Leave
    }

    override def onExit(): Unit = watcher ! "state3.onExit"
  }

  import StateFlow._
  override val stateFlow: StateFlow = State1 >>: State2 >>: State3
}
