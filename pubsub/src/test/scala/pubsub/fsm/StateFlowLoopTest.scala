package pubsub.fsm

import akka.actor.Props
import akka.testkit.TestProbe
import pubsub.BaseTestKit
import pubsub.fsm.StateFlow.loop
import pubsub.fsm.TestState.{LeaveCmd, StayCmd}

class StateFlowLoopTest extends BaseTestKit("StateFlowLoopTest") {

  val stateWatcher = TestProbe()

  "An FSMActor with closed state flow" should {
    "enter state1 after exiting state3" in {

      // should be lazy; otherwise we get 'Forward reference extends over definition of value stateFlow' error
      lazy val stateFlow: StateFlow = TestState("1") >>: TestState("2") >>: TestState("3") >>: loop(stateFlow)

      val ref = system.actorOf(Props(new StateFlowLoopActor(stateFlow, stateName => stateWatcher.ref ! stateName)))
      stateWatcher expectMsg Some("1")

      ref ! LeaveCmd("1")
      stateWatcher expectMsg Some("2")

      ref ! LeaveCmd("2")
      stateWatcher expectMsg Some("3")

      ref ! LeaveCmd("3")
      stateWatcher expectMsg Some("1")
    }
  }
}

object TestState {
  case class LeaveCmd(stateName: String)
  case class StayCmd(stateName: String)

  def apply(name: String): TestState = new TestState(name)
}

class TestState(override val name: String) extends FSMActorState {

  // for pattern matching in receive function
  val Name: String = name

  override def receive: PartialFunction[Any, StateActionResult] = {
    case LeaveCmd(Name) => Leave
    case StayCmd(Name) => Stay
    case msg => throw new IllegalArgumentException(s"Unexpected message $msg")
  }
}

class StateFlowLoopActor(override val stateFlow: StateFlow, onStateChangedCallback: Option[String] => Unit = _ => Unit) extends FSMActor {

  override protected def onStateChanged(newState: Option[String]): Unit = onStateChangedCallback(newState)



  /** Definition of state flow for this FSMActor */
}
