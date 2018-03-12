package pubsub.fsm

import akka.actor.{ActorRef, Props}
import akka.testkit.TestProbe
import org.scalatest.{Matchers, SequentialNestedSuiteExecution}
import pubsub.BaseTestKit
import pubsub.fsm.FSMActorState.actionState

/**
  * Tests that a base FSMActor behaves as expected so it can
  * be used to implement custom actors using FSM logic.
  *
  * Uses test implementation of an FSM actor that makes use of
  * every possible state definition and state transition. Goes
  * through every state and verifies that all the state lifecycle
  * methods will be called by signalling state back with special
  * messages indicating the current state of an actor.
  */
class FSMActorTest extends BaseTestKit("FSMActorTest")
  with Matchers
  with SequentialNestedSuiteExecution {

  /**
    * Watches replies and termination notification from
    * FSM actor under test.
    */
  val watcher = TestProbe()

  /**
    * FSM actor under test.
    */
  val fsmTestActor: ActorRef = system.actorOf(Props(new FSMTestActor(watcher.ref, name => watcher.ref ! name)), "FSMTestActor")

  watcher.watch(fsmTestActor)

  "An FSMTestActor" must {
    "go through State1 and State2 to State3" in {
      watcher.expectMsg("state1.onEnter")
      watcher.expectMsg("state2.onEnter")
      watcher.expectMsg("state3.onEnter")

      watcher.expectMsg(Some("State3"))
    }

    "handle messages by State3 receiver" in {
      fsmTestActor ! "stay in State3"
      watcher.expectMsg("staying in State3")
    }

    "handle unknown messages without leaving current State3" in {
      fsmTestActor ! "unknown message"
      fsmTestActor ! "stay in State3"
      watcher.expectMsg("staying in State3")
    }

    "leave State3 after receiving 'leave' message and stop" in {
      fsmTestActor ! "leave State3"
      watcher.expectMsg("leaving State3")
      watcher.expectMsg("state3.onExit")

      watcher.expectMsg(None)
      watcher.expectTerminated(fsmTestActor)
    }
  }
}

class FSMTestActor(watcher: ActorRef, onStateChangedCallback: Option[String] => Unit = _ => Unit) extends FSMActor {

  override protected def onStateChanged(newState: Option[String]): Unit = onStateChangedCallback(newState)

  /**
    * Invokes an action on entering the state and immediately leaves the state.
    */
  val State1: FSMActorState = actionState("State1") { () => watcher ! "state1.onEnter" }

  /**
    * Invokes an action on entering the state and immediately leaves the state.
    * A verbose version of an actionState.
    */
  val State2: FSMActorState = new FSMActorState {
    override val name: String = "State2"
    override def onEnter(): StateActionResult = {
      watcher ! "state2.onEnter"
      Leave
    }
  }

  /**
    * "Full blown" state.
    * It enters the state invoking onEnter handler. Then it
    * defines receive function for this state. Some of the messages
    * cause the actor to leave the state. After leaving the state
    * onExit handler is invoked.
    */
  val State3: FSMActorState = FSMActorState("State3",
    onEnterCallback = { () => watcher ! "state3.onEnter"; Stay },

    receiveFunc = {
      case "stay in State3" => watcher ! "staying in State3"; Stay
      case "leave State3" => watcher ! "leaving State3"; Leave
    },

    onExitCallback = { () => watcher ! "state3.onExit" }
  )

  import StateFlow._

  /**
    * Definition of state flow.
    * After leaving State3 actor should stop itself.
    */
  override val stateFlow: StateFlow = State1 >>: State2 >>: State3
}
