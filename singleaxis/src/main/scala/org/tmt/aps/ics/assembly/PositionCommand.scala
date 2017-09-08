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

import org.tmt.aps.ics.assembly.motion.MotionAssemblyApi  // TODO: most commands for motion should be in this package too

/**
 * TMT Source Code: 10/22/16.
 */
class PositionCommand(ac: AssemblyContext, sc: SetupConfig, galilHCD: ActorRef, initialState: SingleAxisState, stateActor: Option[ActorRef], 
    saac: SingleAxisAssemblyConfig, maapi: MotionAssemblyApi) extends Actor with ActorLogging {

  import SingleAxisCommandHandler._
  import SingleAxisStateActor._

  def receive: Receive = {
    case CommandStart =>
      
      if (canAcceptCmd(initialState)) {

        log.info("PositionCommand:: CommandStart accepted")

        sendState(startState())
 
        val mySender = sender()

        val scOut = generateHcdCmds(sc)
        
        
        galilHCD ! HcdController.Submit(scOut)
        
        val stateMatcher = completionCriteriaMatcher(sc)

        // sm completionCriteria
        executeMatch(context, stateMatcher, galilHCD, Some(mySender)) {
          case Completed =>
            // sm endingstate
            sendState(endState)
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

  
  
  def canAcceptCommand(startState: SingleAxisState) = {
    canAcceptPositionCmd(startState)
  }
  
  def startState(): SetState = {
    SetState(cmdItem(cmdBusy), moveItem(moveMoving))
  }
  
  // TODO: generalize to a list of SetupConfigs
  def generateHcdCmds(assemblyCmd: SetupConfig): SetupConfig = {
       
    // Position key is encoder units
    SetupConfig(axisMoveCK).add(positionKey -> encoderPosition(assemblyCmd) withUnits encoder)
        
  }
  
  private def encoderPosition(assemblyCmd: SetupConfig): Int = {
    
    val distance = assemblyCmd(maapi.positionParamKey)
    
    // TODO: the actual command depends on the 'type' and 'coord' parameter values
    
    // 1. convert position value to stage coordinates if necessary
    
    // 2. convert control config max and min encoder positions for this assembly into stage range in mm
    
    // 3. match type param
    
    // 3a: type = absolute.  Validate desired stage position within stage range
    
    // 3b: type = relative.  Add position value to current position in stage coordinates, validate desired stage position withing stage range
    
    // 3c: type = deltaFromRef.  Add position value to reference position in stage coordinates, validate desired stage position within stage range.

    

    // Convert distance to encoder units from mm
    val stagePosition = Converter.userPositionToStagePosition(saac.controlConfig, distance.head)
    val encoderPosition = Converter.stagePositionToEncoder(saac.controlConfig, stagePosition)

    log.info(s"Using rangeDistance: ${distance.head} to get stagePosition: $stagePosition to encoder: $encoderPosition")

    encoderPosition
  }
  
  def completionCriteriaMatcher(assemblyCmd: SetupConfig) = {
    
    val stateMatcher = posMatcher(encoderPosition(assemblyCmd))
    
    stateMatcher
  }
  
  def endState(): SetState = {
    SetState(cmdItem(cmdReady), moveItem(moveIndexed))
  }
  
  
}


object PositionCommand {

  def props(ac: AssemblyContext, sc: SetupConfig, galilHCD: ActorRef, startState: SingleAxisState, stateActor: Option[ActorRef], 
      saac: SingleAxisAssemblyConfig, aapi: AssemblyApi): Props =
    Props(classOf[PositionCommand], ac, sc, galilHCD, startState, stateActor, saac)
}
