package pubsub

import akka.actor.{ActorSystem, SupervisorStrategyConfigurator}
import akka.testkit.{ImplicitSender, TestKit}
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{BeforeAndAfterAll, WordSpecLike}

import scala.collection.JavaConverters._
import scala.language.implicitConversions
import scala.reflect.ClassTag

/** Type-safe ActorSystem config builder */
class ActorSystemConfig(val options: Map[String, AnyRef] = Map()) {

  /** Adds a new option to the config and returns new ActorSystemConfig object */
  def withOption(option: String, value: AnyRef): ActorSystemConfig =
    new ActorSystemConfig(options + (option -> value))

  /** Sets supervisor strategy for the `/user` guardian */
  def withGuardianSupervisorStrategy[T <: SupervisorStrategyConfigurator]()(implicit tag: ClassTag[T]): ActorSystemConfig =
    withOption("akka.actor.guardian-supervisor-strategy", tag.runtimeClass.getName)
}

object ActorSystemConfig {

  def apply(): ActorSystemConfig = new ActorSystemConfig()

  implicit def asConfig(actorSystemConfig: ActorSystemConfig): Option[Config] =
    Some(ConfigFactory.parseMap(actorSystemConfig.options.asJava))
}

/**
  * Base TestKit for testing actors.
  * @param actorSystemName Actor system name.
  */
class BaseTestKit(val actorSystemName: String, config: Option[Config] = None) extends TestKit(ActorSystem(actorSystemName, config))
  with WordSpecLike
  with BeforeAndAfterAll
  with ImplicitSender {

  /**
    * Shuts down actor system after all tests are finished, so you
    * don't have to bother to do it manually in every test suite.
    */
  override def afterAll: Unit = {
    TestKit.shutdownActorSystem(system)
  }
}
