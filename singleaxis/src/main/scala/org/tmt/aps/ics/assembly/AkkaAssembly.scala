package org.tmt.aps.ics.assembly

import java.io.File

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import csw.services.alarms.AlarmService
import csw.services.ccs.AssemblyMessages.{DiagnosticMode, OperationsMode}
// 0.4 import csw.services.ccs.SequentialExecutor.{ExecuteOne, StartTheSequence}
import csw.services.ccs.Validation.ValidationList
import csw.services.ccs.Validation._
import csw.services.ccs.{AssemblyController, SequentialExecutor, Validation}
import csw.services.cs.akka.ConfigServiceClient
import csw.services.events.{EventService, TelemetryService}
import csw.services.loc.LocationService._
import csw.services.loc._
import csw.services.pkg.Component.AssemblyInfo
import csw.services.pkg.{Assembly, Supervisor}
import csw.util.config.Configurations.SetupConfigArg

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps

//import scala.concurrent._

import csw.services.pkg.Hcd

/**
 * Top Level Actor for Single Axis Assembly
 *
 * SingleAxisAssembly starts up the component doing the following:
 * creating all needed actors,
 * handling initialization,
 * participating in lifecycle with Supervisor,
 * handles locations for distribution throughout component
 * receives comamnds and forwards them to the CommandHandler by extending the AssemblyController
 */
abstract class AkkaAssembly(val info: AssemblyInfo, supervisor: ActorRef) extends Assembly with AssemblyController {

  import Supervisor._
  import AkkaAssembly._

  implicit val system: ActorSystem = context.system

  protected var galilHCD: Option[ActorRef] = badHCDReference

  protected var eventService: Option[EventService] = badEventService

  protected var telemetryService: Option[TelemetryService] = badTelemetryService

  protected var alarmService: Option[AlarmService] = badAlarmService

  private val trackerSubscriber = context.actorOf(LocationSubscriberActor.props)

  implicit val ac: AssemblyContext = AssemblyContext(info)

  protected var commandHandler: ActorRef = _

  protected var telemetryGenerator: ActorRef = _

  protected var eventPublisher: ActorRef = _

  def initTelemetryGenerator: ActorRef

  def initCommandHandler: ActorRef

  def initialize() = {
    try {

      // Start tracking the components we command
      log.info("Connections: " + info.connections)
      trackerSubscriber ! LocationSubscriberActor.Subscribe

      // This actor handles all telemetry and system event publishing
      eventPublisher = context.actorOf(SingleAxisPublisher.props(ac, None))

      commandHandler = initCommandHandler

      telemetryGenerator = initTelemetryGenerator

      // This tracks the HCD
      LocationSubscriberActor.trackConnections(info.connections, trackerSubscriber)
      // This tracks required services
      LocationSubscriberActor.trackConnection(EventService.eventServiceConnection(), trackerSubscriber)
      LocationSubscriberActor.trackConnection(TelemetryService.telemetryServiceConnection(), trackerSubscriber)
      LocationSubscriberActor.trackConnection(AlarmService.alarmServiceConnection(), trackerSubscriber)

      log.info("SM initialization complete");

    } catch {
      case ex: Exception =>
        log.error("execption in initialization")
        supervisor ! InitializeFailure(ex.getMessage)
        null
    }
  }

  def receive: Receive = initializingReceive

  /**
   * This contains only commands that can be received during intialization
   *
   * @return Receive is a partial function
   */
  def initializingReceive: Receive = locationReceive orElse {

    case Running =>
      // When Running is received, transition to running Receive
      log.info("becoming runningReceive")
      // Set the operational cmd state to "ready" according to spec-this is propagated to other actors
      //state(cmd = cmdReady)

      log.info("COMMAND HANDLER = " + commandHandler)

      context.become(runningReceive)
    case x => log.error(s"Unexpected message in initializingReceive: $x")
  }

  /**
   * This Receive partial function processes changes to the services and galilHCD
   * Ideally, we would wait for all services before sending Started, but it's not done yet
   */
  def locationReceive: Receive = {
    case location: Location =>
      location match {

        case l: ResolvedAkkaLocation =>
          log.info(s"Got actorRef: ${l.actorRef}")
          galilHCD = l.actorRef
          // When the HCD is located, Started is sent to Supervisor
          checkServicesResolved
          onResolvedHcd(galilHCD)

        case h: ResolvedHttpLocation =>
          log.info(s"HTTP Service Damn it: ${h.connection}")

        case t: ResolvedTcpLocation =>
          log.info(s"Received TCP Location: ${t.connection}")
          // Verify that it is the event service
          if (t.connection == EventService.eventServiceConnection()) {
            log.info(s"Assembly received ES connection: $t")
            // Setting var here!
            eventService = Some(EventService.get(t.host, t.port))
            log.info(s"Event Service at: $eventService")
            //checkServicesResolved
            onResolvedEventService(eventService)
          }
          if (t.connection == TelemetryService.telemetryServiceConnection()) {
            log.info(s"Assembly received TS connection: $t")
            // Setting var here!
            telemetryService = Some(TelemetryService.get(t.host, t.port))
            log.info(s"Telemetry Service at: $telemetryService")
            //checkServicesResolved
            onResolvedTelemetryService(telemetryService)
          }
          if (t.connection == AlarmService.alarmServiceConnection()) {
            implicit val timeout = Timeout(10.seconds)
            log.info(s"Assembly received AS connection: $t")
            // Setting var here!
            alarmService = Some(AlarmService.get(t.host, t.port))
            log.info(s"Alarm Service at: $alarmService")
            //checkServicesResolved
            onResolvedAlarmService(alarmService)
          }

        case u: Unresolved =>
          log.info(s"Unresolved: ${u.connection}")
          if (u.connection == EventService.eventServiceConnection()) {
            eventService = badEventService
            onUnresolvedEventService
          }

          if (u.connection.componentId == ac.hcdComponentId) {
            galilHCD = badHCDReference
            onUnresolvedHcd
          }

        case ut: UnTrackedLocation =>
          log.info(s"UnTracked: ${ut.connection}")
      }
  }

  // override hooks for service/Hcd resolve/unresolve

  def onResolvedHcd(hcd: Option[ActorRef]) = {}
  def onResolvedEventService(eventService: Option[EventService]) = {}
  def onResolvedTelemetryService(telemetryService: Option[TelemetryService]) = {}
  def onResolvedAlarmService(alarmService: Option[AlarmService]) = {}
  def onUnresolvedHcd = {}
  def onUnresolvedEventService = {}
  def onUnresolvedTelemetryService = {}
  def onUnresolvedAlarmService = {}

  // TODO: have a function that tests all services and if all are resolved, send a start message to the supervisor
  def checkServicesResolved(): Unit = {
    // check all services and if all are resolved, send a started message to Supervisor.
    // only do this once.

    if (galilHCD != badHCDReference) supervisor ! Initialized
  }

  // Receive partial function used when in Running state
  // def runningReceive: Receive = locationReceive orElse diagReceive orElse controllerReceive orElse lifecycleReceivePF orElse unhandledPF
  def runningReceive: Receive = locationReceive orElse controllerReceive orElse lifecycleReceivePF orElse unhandledPF

  // Receive partial function for handling the diagnostic commands

  def diagReceive: Receive = {
    case DiagnosticMode(hint) =>
      log.debug(s"Received diagnostic mode: $hint")
      telemetryGenerator ! TelemetryGenerator.DiagnosticState
    case OperationsMode => {
      log.debug(s"Received operations mode")
      telemetryGenerator ! TelemetryGenerator.OperationsState
    }
  }

  // Receive partial function to handle runtime lifecycle messages
  def lifecycleReceivePF: Receive = {
    case Running =>
      // Already running so ignore
      onRunning
    case RunningOffline =>
      // Here we do anything that we need to do be an offline, which means running and ready but not currently in use
      log.info("Received running offline")
    case DoRestart =>
      log.info("Received dorestart")
      onRestart
    case DoShutdown =>
      log.info("SingleAxisAssembly received doshutdown")
      // Ask our HCD to shutdown, then return complete
      beforeShutdown
      galilHCD.foreach(_ ! DoShutdown)
      supervisor ! ShutdownComplete
      afterShutdown
    case LifecycleFailureInfo(state: LifecycleState, reason: String) =>
      // This is an error conditin so log it
      log.error(s"SingleAxisAssembly received failed lifecycle state: $state for reason: $reason")
      onLifecycleFailure
  }

  def onRunning = {}
  def onRestart = {}
  def beforeShutdown = {}
  def afterShutdown = {}
  def onLifecycleFailure = {}

  // Catchall unhandled message receive
  def unhandledPF: Receive = {
    case x => log.error(s"Unexpected message: unhandledPF: $x")
  }

  /**
   * Function that overrides AssemblyController setup processes incoming SetupConfigArg messages
   * @param sca received SetupConfgiArg
   * @param commandOriginator the sender of the command
   * @return a validation object that indicates if the received config is valid
   */
  override def setup(sca: SetupConfigArg, commandOriginator: Option[ActorRef]): ValidationList = {
    // Returns validations for all
    val validations: ValidationList = validateSequenceConfigArg(sca)
    if (Validation.isAllValid(validations)) {
      // Create a SequentialExecutor to process all SetupConfigs
      context.actorOf(SequentialExecutor.props(commandHandler, sca, commandOriginator))
    }
    validations
  }

  def validateSequenceConfigArg(sca: SetupConfigArg): ValidationList = {
    List[Validation]()
  }

}

/**
 * All assembly messages are indicated here
 */
object AkkaAssembly {

  // Get the single axis assembly config file from the config service, or use the given resource file if that doesn't work
  //val singleAxisConfigFile = new File("poc/singleAxis")
  //val resource = new File("singleAxisAssembly.conf")

  // --------- Keys/Messages used by Multiple Components
  /**
   * The message is used within the Assembly to update actors when the Galil HCD goes up and down and up again
   *
   * @param galilHCD the ActorRef of the galilHCD or None
   */
  case class UpdateGalilHCD(galilHCD: Option[ActorRef])

  private val badEventService: Option[EventService] = None
  private val badTelemetryService: Option[TelemetryService] = None
  private val badAlarmService: Option[AlarmService] = None
  private val badHCDReference = None
}
