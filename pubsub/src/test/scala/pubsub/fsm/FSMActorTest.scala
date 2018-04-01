package pubsub.fsm

import akka.actor.Props
import akka.testkit.TestProbe
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, SequentialNestedSuiteExecution}
import pubsub.utils.{BaseTestKit, PartialFunctionMockFactory}

import scala.concurrent.duration._
import scala.language.postfixOps

//noinspection TypeAnnotation,ScalaStyle
class FSMActorTest extends BaseTestKit("FSMActorTest")
  with Matchers
  with MockFactory
  with PartialFunctionMockFactory
  with SequentialNestedSuiteExecution {

  /** Implements all the necessary plumbing for mocking [[pubsub.fsm.StateFlow]] */
  case class MockStateFlow[T](stateName: String) {
    val state = mock[FSMActorState[T]]
    val nextState = mockFunction[FSMActorState[T], Option[StateFlow[T]]]
    val stateFlow = new StateFlow[T](state, nextState)
    val receive = mockPartialFunction[(T, Any), StateActionResult[T]]

    (state.receive _).stubs().returning(receive)
    (state.name _).stubs().returning(stateName)

    def expectNextState(nextStateFlow: MockStateFlow[T]): MockStateFlow[T] = {
      nextState.expects(state).returning(Some(nextStateFlow.stateFlow))
      nextStateFlow
    }

    def expectNoState(): Unit = {
      nextState.expects(state).returning(None)
    }
  }

  /** Runs FSMActor under test */
  def runFSMActor[T](stateWatcher: TestProbe, _initialState: T, _stateFlow: StateFlow[T]) = system.actorOf(Props(new FSMActor[T] {
    stateWatcher.watch(self)

    override protected val onStateChanged = { newState: Option[String] => stateWatcher.ref ! newState }
    override protected val initialState: T = _initialState
    override protected val stateFlow: StateFlow[T] = _stateFlow
  }))


  "An FSMActor should run through its lifecycle with respect to its state flow" in {

    // setting expectations
    val mockFlow1 = MockStateFlow[Int]("state1")
    (mockFlow1.state.onEnter _).expects(0).returning(Leave(1))
    (mockFlow1.state.onExit _).expects()

    val mockFlow2 = mockFlow1.expectNextState(MockStateFlow[Int]("state2"))
    (mockFlow2.state.onEnter _).expects(1).returning(Stay(2))
    mockFlow2.receive.mock.expects(2, "state2.stay").returning(Stay(3))
    mockFlow2.receive.mock.expects(3, "state2.leave").returning(Leave(4))
    (mockFlow2.state.onExit _).expects()

    mockFlow2.expectNoState()

    // running the actor
    val stateWatcher = TestProbe("stateWatcher")
    val fsmActor = runFSMActor(stateWatcher, 0, mockFlow1.stateFlow)
    stateWatcher.expectMsg(Some(mockFlow2.stateName))

    fsmActor ! "state2.stay"
    stateWatcher.expectNoMessage(100 millis)

    fsmActor ! "state2.leave"
    stateWatcher.expectMsg(None)
    stateWatcher.expectTerminated(fsmActor)
  }
}
