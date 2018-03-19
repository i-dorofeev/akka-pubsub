package pubsub.fsm

import org.scalamock.scalatest.MockFactory
import org.scalatest.WordSpecLike

import StateFlow._

/** Tests DSL for creating state flows */
class StateFlowDslTest extends WordSpecLike with MockFactory {

  "Implicit conversion from FSMActorState to StateFlow should work" in {
    val state1 = stub[FSMActorState[Any]]

    val flow1: StateFlow[Any] = state1
    assert(flow1.state == state1)

    val flow2 = flow1.nextState.apply(state1)
    assert(flow2.isEmpty)
  }

  "Flow operator >>: should work" in {
    val state1 = stub[FSMActorState[Any]]
    val state2 = stub[FSMActorState[Any]]

    val flow1 = state1 >>: state2
    assert(flow1.state == state1)

    val flow2 = flow1.nextState.apply(state1)
    assertResult(Some(state2)) { flow2.map(_.state) }

    val flow3 = flow2.get.nextState.apply(state2)
    assert(flow3.isEmpty)
  }

  "Loop should work" in {
    val state1 = stub[FSMActorState[Any]]
    val state2 = stub[FSMActorState[Any]]

    object FlowHolder {
      // we must define flow in an object or a class
      // otherwise we'll get a compiler error
      val flow: StateFlow[Any] = state1 >>: state2 >>: loop(flow)
    }

    val flow1 = FlowHolder.flow
    assert(flow1.state == state1)

    val flow2 = flow1.nextState.apply(state1)
    assertResult(Some(state2)) { flow2.map(_.state) }

    val flow3 = flow2.get.nextState.apply(state2)
    assertResult(Some(state1)) { flow3.map(_.state) }
  }
}
