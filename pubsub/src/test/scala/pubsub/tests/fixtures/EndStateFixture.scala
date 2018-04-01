package pubsub.tests.fixtures

import org.scalamock.handlers.{CallHandler1, Verify}
import org.scalamock.scalatest.MockFactory
import pubsub.fsm.{FSMActorState, Leave, StateActionResult}

trait EndStateFixture extends MockFactory {

  trait EndState[A] {

    // End state receives the state value from the state under test and ends the state flow
    val EndState: FSMActorState[A] = stub[FSMActorState[A]]
    (EndState.onEnter _).when(*).onCall { i: A => Leave(i) }
    (EndState.name _).when().returning("endState")

    /** Verifies the state value after leaving the state under test.
      *
      * @param expectedStateValue Expected state value.
      */
    def verifyStateValue(expectedStateValue: A): CallHandler1[A, StateActionResult[A]] with Verify =
      (EndState.onEnter _).verify(expectedStateValue)
  }
}
