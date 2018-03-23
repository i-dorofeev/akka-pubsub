package pubsub.fsm

/** Result of an action performed within a state. Can be either [[Stay]] or [[Leave]]
  * @tparam A Type of the state data.
  */
sealed trait StateActionResult[A]

/** Indicates that the state should remain active to process messages.
  * @param stateData Data associated with the state to pass along the state flow.
  * @tparam A Type of the state data.
  */
case class Stay[A](stateData: A) extends StateActionResult[A]

/** Indicates that the state is no longer able to accept new messages.
  * @param stateData Data associated with the state to pass along the state flow.
  * @tparam A Type of the state data.
  */
case class Leave[A](stateData: A) extends StateActionResult[A]
