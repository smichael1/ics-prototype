package org.tmt.aps.ics.assembly

import akka.actor.{Actor, ActorContext, ActorLogging, ActorRef, ActorSystem, PoisonPill, Props}
import akka.pattern.ask
import akka.util.Timeout

import org.tmt.aps.ics.hcd.GalilHCD
import org.tmt.aps.ics.hcd.GalilHCD._
import csw.services.ccs.CommandStatus._
import csw.services.ccs.SequentialExecutor.{CommandStart, ExecuteOne, StopCurrentCommand}
import csw.services.ccs.MultiStateMatcherActor.StartMatch
import csw.services.ccs.{DemandMatcher, MultiStateMatcherActor, StateMatcher}
import csw.services.ccs.Validation.{RequiredHCDUnavailableIssue, UnsupportedCommandInStateIssue, WrongInternalStateIssue}
import csw.services.events.EventService
import csw.services.loc.LocationService._
import csw.services.loc.LocationSubscriberClient
import csw.util.config.Configurations.SetupConfig
import csw.util.config.StateVariable.DemandState

import org.tmt.aps.ics.assembly.motion.MultiAxisMotionAssemblyApi
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * TMT Source Code: 9/21/16.
 */
class SingleAxisCommandHandler(ac: AssemblyContext, galilHCDIn: Option[ActorRef], singleAxisPublisher: ActorRef, maac: MultiAxisAssemblyConfig,
                               maapi: MultiAxisMotionAssemblyApi)
    extends Actor with ActorLogging with LocationSubscriberClient with SingleAxisStateClient {

  import context.dispatcher
  import SingleAxisStateActor._
  import SingleAxisCommandHandler._
  import ac._

  implicit val system: ActorSystem = context.system
  implicit val timeout = Timeout(5.seconds)

  //override val prefix = ac.info.prefix
  private val badHCDReference = context.system.deadLetters

  private var galilHCD: ActorRef = galilHCDIn.getOrElse(badHCDReference)
  private def isHCDAvailable: Boolean = galilHCD != badHCDReference

  private val badEventService = None
  var eventService: Option[EventService] = badEventService

  // The actor for managing the persistent assembly state as defined in the spec is here, it is passed to each command
  private val singleAxisStateActor = context.actorOf(SingleAxisStateActor.props(singleAxisPublisher))

  def receive: Receive = waitForExecuteReceive()

  def handleLocations(location: Location): Unit = {
    location match {

      case l: ResolvedAkkaLocation =>
        log.debug(s"CommandHandler receive an actorRef: ${l.actorRef}")
        galilHCD = l.actorRef.getOrElse(badHCDReference)

      case t: ResolvedTcpLocation =>
        log.info(s"Received TCP Location: ${t.connection} from ${sender()}")
        // Verify that it is the event service
        if (t.connection == EventService.eventServiceConnection()) {
          log.info(s"Subscriber received connection: $t from ${sender()}")
          // Setting var here!
          eventService = Some(EventService.get(t.host, t.port))
          log.info(s"Event Service at: $eventService")
        }

      case u: Unresolved =>
        log.info(s"Unresolved: ${u.connection}")
        if (u.connection == EventService.eventServiceConnection()) eventService = badEventService
        if (u.connection.componentId == ac.hcdComponentId) galilHCD = badHCDReference

      case default =>
        log.info(s"CommandHandler received some other location: $default")
    }
  }

  def waitForExecuteReceive(): Receive = stateReceive orElse {

    case l: Location =>
      handleLocations(l)

    case ExecuteOne(sc, commandOriginator) =>

      log.info("ExecuteOne reached");

      // sm -  user specific Handler should take the configKey as input and return the actor that will handle the command
      // each "Command" actor should actually subclass from a single Command actor

      // get the correct singleAxisConfig out of the passed MultiAxisAssemblyConfig given the passed setupConfig

      log.debug(s"axisNameKey = ${maapi.axisNameParamKey}")
      log.debug(s"param = ${sc(maapi.axisNameParamKey)}")
      log.debug(s"values = ${sc(maapi.axisNameParamKey).values}")

      val axisName = sc(maapi.axisNameParamKey).values.head
      val sac = maac.axesmap(axisName)

      sc.configKey match {
        case ac.compHelper.initCK =>
          log.info("Init not fully implemented -- only sets state ready!")
          Await.ready(singleAxisStateActor ? SetState(cmdItem(cmdReady), moveItem(moveUnindexed)), timeout.duration)
          commandOriginator.foreach(_ ! Completed)

        case ac.compHelper.positionCK =>
          if (isHCDAvailable) {
            log.info("ExecuteOne: positionCK");
            val positionActorRef = context.actorOf(PositionCommand.props(ac, sc, galilHCD, currentState, Some(singleAxisStateActor), sac, maapi))
            //log.info("positionActorRef = " + positionActorRef)
            context.become(actorExecutingReceive(positionActorRef, commandOriginator))
            self ! CommandStart
          } else hcdNotAvailableResponse(commandOriginator)

        case ac.compHelper.datumCK =>
          if (isHCDAvailable) {
            log.info("ExecuteOne: datumCK");
            val datumActorRef = context.actorOf(DatumCommand.props(sc, galilHCD, currentState, Some(singleAxisStateActor), sac, maapi))

            context.become(actorExecutingReceive(datumActorRef, commandOriginator))
            self ! CommandStart
          } else hcdNotAvailableResponse(commandOriginator)

        case otherCommand =>
          log.error(s"SingleAxisCommandHandler:noFollowReceive received an unknown command: $otherCommand  $otherCommand.prefix")
          commandOriginator.foreach(_ ! Invalid(UnsupportedCommandInStateIssue(s"""Single Axis assembly does not support the command \"${otherCommand.prefix}\" in the current state.""")))
      }

    case x => log.error(s"SingleAxisCommandHandler:noFollowReceive received an unknown message: $x")
  }

  def hcdNotAvailableResponse(commandOriginator: Option[ActorRef]): Unit = {
    commandOriginator.foreach(_ ! NoLongerValid(RequiredHCDUnavailableIssue(s"${ac.hcdComponentId} is not available")))
  }

  def actorExecutingReceive(currentCommand: ActorRef, commandOriginator: Option[ActorRef]): Receive = stateReceive orElse {
    case CommandStart =>
      import context.dispatcher

      // Execute the command actor asynchronously, pass the command status back, kill the actor and go back to waiting
      // does the for expression wait on the Future?
      log.info("In CommandStart, currentCommand = ")
      for {
        cs <- (currentCommand ? CommandStart).mapTo[CommandStatus]
      } {
        commandOriginator.foreach(_ ! cs)
        currentCommand ! PoisonPill
        self ! CommandDone
        //        context.become(waitForExecuteReceive())
      }

    case CommandDone =>
      context.become(waitForExecuteReceive())

    case SetupConfig(ac.compHelper.stopCK, _) =>
      log.debug("actorExecutingReceive: Stop CK")
      closeDownMotionCommand(currentCommand, commandOriginator)

    case ExecuteOne(SetupConfig(ac.compHelper.stopCK, _), _) =>
      log.debug("actorExecutingReceive: ExecuteOneStop")
      closeDownMotionCommand(currentCommand, commandOriginator)

    case x => log.error(s"SingleAxisCommandHandler:actorExecutingReceive received an unknown message: $x")
  }

  private def closeDownMotionCommand(currentCommand: ActorRef, commandOriginator: Option[ActorRef]): Unit = {
    currentCommand ! StopCurrentCommand
    currentCommand ! PoisonPill
    context.become(waitForExecuteReceive())
    commandOriginator.foreach(_ ! Cancelled)
  }

}

object SingleAxisCommandHandler {

  // message sent too self when command completes
  private case object CommandDone

  def props(assemblyContext: AssemblyContext, galilHCDIn: Option[ActorRef], telemetryGenerator: ActorRef, maac: MultiAxisAssemblyConfig, maapi: MultiAxisMotionAssemblyApi) =
    Props(new SingleAxisCommandHandler(assemblyContext, galilHCDIn, telemetryGenerator, maac, maapi))

  def executeMatch(context: ActorContext, stateMatcher: StateMatcher, currentStateSource: ActorRef, replyTo: Option[ActorRef] = None,
                   timeout: Timeout = Timeout(5.seconds))(codeBlock: PartialFunction[CommandStatus, Unit]): Unit = {
    import context.dispatcher
    implicit val t = Timeout(timeout.duration + 1.seconds)

    val matcher = context.actorOf(MultiStateMatcherActor.props(currentStateSource, timeout))
    for {
      cmdStatus <- (matcher ? StartMatch(stateMatcher)).mapTo[CommandStatus]
    } {
      codeBlock(cmdStatus)
      replyTo.foreach(_ ! cmdStatus)
    }
  }

  def idleMatcher: DemandMatcher = DemandMatcher(DemandState(axisStateCK).add(stateKey -> GalilHCD.AXIS_IDLE))

  def posMatcher(position: Int): DemandMatcher =
    DemandMatcher(DemandState(axisStateCK).madd(stateKey -> GalilHCD.AXIS_IDLE, positionKey -> position))

}

