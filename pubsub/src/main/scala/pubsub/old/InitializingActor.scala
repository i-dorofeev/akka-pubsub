package pubsub.old

import akka.actor.{Actor, ActorLogging, Stash}

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Provides abstraction for actors that need to undergo
  * non-trivial asynchronous initialization process.
  *
  * Defines three states:
  *   - Initializing
  */
abstract class InitializingActor extends Actor
  with Stash
  with ActorLogging {

  import context._

  override def preStart(): Unit = {
    log.debug("Initializing...")
    init.onComplete {
      case Success(_) =>
        self ! "initialized"
      case Failure(ex) =>
        self ! ex
    }
  }

  private def initializing: Receive = {
    case "initialized" if sender() == self =>
      unstashAll()
      context.become(working)
      log.debug("Successfully initialized and switched to work mode")

    case ex: Throwable if sender() == self =>
      unstashAll()
      context.become(failed)
      log.error("Failed to initialize: {}", ex)

    case msg =>
      stash()
      log.debug("Initializing... - stashed {}", msg)
  }

  override def receive: Receive = initializing

  def init: Future[Unit]
  def working: Receive
  def failed: Receive = ???
}