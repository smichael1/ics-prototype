package org.tmt.aps.ics.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import org.tmt.aps.ics.assembly.SingleAxisStateActor.SingleAxisState
import org.tmt.aps.ics.hcd.GalilHCD._
import csw.services.ccs.CommandStatus.{Completed, Error, NoLongerValid}
import csw.services.ccs.HcdController
import csw.services.ccs.SequentialExecutor.{CommandStart, StopCurrentCommand}
import csw.services.ccs.Validation.WrongInternalStateIssue
import csw.util.config.Configurations.SetupConfig
import akka.pattern.ask
import akka.util.Timeout

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * TMT Source Code: 10/21/16.
 */
class DatumCommand(sc: SetupConfig, galilHCD: ActorRef, startState: SingleAxisState, stateActor: Option[ActorRef]) extends Actor with ActorLogging {
  import SingleAxisCommandHandler._
  import SingleAxisStateActor._

  // Not using stateReceive since no state updates are needed here only writes
  def receive: Receive = {
    case CommandStart =>
      if (startState.cmd.head == cmdUninitialized) {
        sender() ! NoLongerValid(WrongInternalStateIssue(s"Assembly state of ${cmd(startState)}/${move(startState)} does not allow datum"))
      } else {
        val mySender = sender()
        sendState(SetState(cmdItem(cmdBusy), moveItem(moveIndexing)))
        galilHCD ! HcdController.Submit(SetupConfig(axisDatumCK))
        SingleAxisCommandHandler.executeMatch(context, idleMatcher, galilHCD, Some(mySender)) {
          case Completed =>
            sendState(SetState(cmdReady, moveIndexed))
          case Error(message) =>
            log.error(s"Data command match failed with error: $message")
        }
      }
    case StopCurrentCommand =>
      log.debug(">>  DATUM STOPPED")
      galilHCD ! HcdController.Submit(cancelSC)

    case StateWasSet(b) => // ignore confirmation
  }

  private def sendState(setState: SetState): Unit = {
    implicit val timeout = Timeout(5.seconds)
    stateActor.foreach(actorRef => Await.ready(actorRef ? setState, timeout.duration))
  }
}

object DatumCommand {
  def props(sc: SetupConfig, galilHCD: ActorRef, startState: SingleAxisState, stateActor: Option[ActorRef]): Props =
    Props(classOf[DatumCommand], sc, galilHCD, startState, stateActor)
}
