package pubsub.fsm

import akka.actor.Props
import akka.testkit.TestProbe
import pubsub.BaseTestKit
import pubsub.fsm.FSMActorState.FSMReceive
import pubsub.fsm.StateFlow.loop
import pubsub.fsm.TestState.{LeaveCmd, StayCmd}

class ClosedStateFlowTest extends BaseTestKit("ClosedStateFlowTest") {

  val stateWatcher = TestProbe()

  "An FSMActor with closed state flow" should {
    "loop over states" in {

     val ref = system.actorOf(Props(new FSMActor[Unit] {
        val initialState: Unit = Unit
        val stateFlow: StateFlow[Unit] = TestState("1") >>: TestState("2") >>: loop(stateFlow)
        override def onStateChanged(newState: Option[String]): Unit = stateWatcher.ref ! newState
      }))

      stateWatcher expectMsg Some("1")

      ref ! LeaveCmd("1")
      stateWatcher expectMsg Some("2")

      ref ! LeaveCmd("2")
      stateWatcher expectMsg Some("1")
    }
  }
}

object TestState {
  case class LeaveCmd(stateName: String)
  case class StayCmd(stateName: String)

  def apply(name: String): TestState = new TestState(name)
}

class TestState(override val name: String) extends FSMActorState[Unit] {

  // for pattern matching in receive function
  val Name: String = name

  override def receive: FSMReceive[Unit] = {
    case (state, LeaveCmd(Name)) => Leave(state)
    case (state, StayCmd(Name)) => Stay(state)
    case msg => throw new IllegalArgumentException(s"Unexpected message $msg")
  }
}
