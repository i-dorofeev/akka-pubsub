package pubsub.tests.fsm

import org.scalamock.scalatest.MockFactory
import org.scalatest.WordSpecLike
import pubsub.fsm.{FSMActorState, Leave}

class FSMActorStateTest extends WordSpecLike with MockFactory {

  "actionState constructor should create the state that executes an action and leaves immediately" in {
    import FSMActorState._

    // we expect this function to be called once when entering the state
    val actionMock = mockFunction[Unit]
    actionMock.expects()

    // constructing the state
    val state = actionState[Int]("testAction") { () => actionMock() }

    // simulating entering the state
    val status = state.onEnter(0)

    // expecting the signal to leave immediately
    assert(status == Leave(0))
  }
}
