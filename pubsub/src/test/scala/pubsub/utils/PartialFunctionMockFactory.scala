package pubsub.utils

import org.scalamock.function.MockFunction1
import org.scalamock.scalatest.MockFactory

/** Mock for a [[PartialFunction]]
  * @param mock Function mocking apply method
  * @tparam A Type of input parameter
  * @tparam B Type of return value
  */
class PartialFunctionMock[A, B](val mock: MockFunction1[A, B]) extends PartialFunction[A, B] {
  override def isDefinedAt(x: A): Boolean = true
  override def apply(v1: A): B = mock(v1)
}

/** Factory for [[pubsub.utils.PartialFunctionMock[A, B]]] */
trait PartialFunctionMockFactory extends MockFactory {
  def mockPartialFunction[A, B]: PartialFunctionMock[A, B] = new PartialFunctionMock(mockFunction[A, B])
}
