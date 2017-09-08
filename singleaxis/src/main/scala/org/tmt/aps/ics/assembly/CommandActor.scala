package org.tmt.aps.ics.assembly

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import org.tmt.aps.ics.assembly.SingleAxisStateActor._
import org.tmt.aps.ics.hcd.GalilHCD._
import csw.services.ccs.CommandStatus.{Completed, Error, NoLongerValid}
import csw.services.ccs.HcdController
import csw.services.ccs.SequentialExecutor.{CommandStart, StopCurrentCommand}
import csw.services.ccs.Validation.WrongInternalStateIssue
import csw.util.config.Configurations.SetupConfig
import csw.util.config.UnitsOfMeasure.encoder

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask

import csw.services.ccs.{DemandMatcher, MultiStateMatcherActor, StateMatcher}

/**
 * TMT Source Code: 10/22/16.
 */
class CommandActor(ac: AssemblyContext, sc: SetupConfig, galilHCD: ActorRef, initialState: SingleAxisState, stateActor: Option[ActorRef], 
    saac: SingleAxisAssemblyConfig, commandHandler: CommandHandler) extends Actor with ActorLogging {

  import SingleAxisCommandHandler._
  import SingleAxisStateActor._

  def receive: Receive = {
    case CommandStart =>
      
      if (canAcceptCmd(initialState)) {

        log.info("PositionCommand:: CommandStart accepted")

        sendState(commandHandler.startState())
 
        val mySender = sender()

        val scOut = commandHandler.generateHcdCmds(sc)
        
        galilHCD ! HcdController.Submit(scOut)
        
        val stateMatcher = commandHandler.completionCriteriaMatcher(sc)

        // sm completionCriteria
        executeMatch(context, commandHandler.completionCriteriaMatcher(sc), galilHCD, Some(mySender)) {
          case Completed =>
            // sm endingstate
            sendState(commandHandler.endState)
          case Error(message) =>
            log.error(s"Position command match failed with message: $message")
        }
      } else {

        log.info("PositionCommand:: CommandStart not accepted")
        sender() ! NoLongerValid(WrongInternalStateIssue(s"Assembly state of ${cmd(initialState)}/${move(initialState)} does not allow motion"))

      }
    case StopCurrentCommand =>
      log.debug("Move command -- STOP")
      galilHCD ! HcdController.Submit(cancelSC)
  }

  private def sendState(setState: SetState): Unit = {
    implicit val timeout = Timeout(5.seconds)
    stateActor.foreach(actorRef => Await.ready(actorRef ? setState, timeout.duration))
  }

  
  

  
}

trait CommandHandler {
    
  def canAcceptCommand(startState: SingleAxisState)
  
  def startState(): SetState 

  def generateHcdCmds(assemblyCmd: SetupConfig): SetupConfig
 
  def completionCriteriaMatcher(assemblyCmd: SetupConfig): DemandMatcher
 
  def endState(): SetState 
  
}

object CommandActor {

  def props(ac: AssemblyContext, sc: SetupConfig, galilHCD: ActorRef, startState: SingleAxisState, stateActor: Option[ActorRef], 
      saac: SingleAxisAssemblyConfig, commandHandler: CommandHandler): Props =
    Props(classOf[PositionCommand], ac, sc, galilHCD, startState, stateActor, saac, commandHandler)
}
