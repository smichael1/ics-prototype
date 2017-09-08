package org.tmt.aps.ics.assembly

import java.io.File

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.Timeout
import org.tmt.aps.ics.assembly.SingleAxisAssemblyConfig.{SingleAxisControlConfig}

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
import org.tmt.aps.ics.assembly.motion.MotionTelemetryGenerationHandler

import org.tmt.aps.ics.assembly.motion.MultiAxisMotionAssemblyApi  // FIXME - should not be here

//import scala.concurrent._

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
class SingleAxisAssembly(info: AssemblyInfo, supervisor: ActorRef) extends AkkaAssembly(info, supervisor) {

  import SingleAxisAssembly._

  implicit val saac: SingleAxisAssemblyConfig = generateAssemblyConfig()
  implicit val aapi: AssemblyApi = MultiAxisMotionAssemblyApi("duh") // TODO: populate this

  init

  def init() = {
    super.initialize()
  }

  def initTelemetryGenerator(): ActorRef = {

    // This sets up the diagnostic data publisher - setting Var here
    context.actorOf(TelemetryGenerator.props(ac, galilHCD, Some(eventPublisher), new MotionTelemetryGenerationHandler(saac)))

  }

  def initCommandHandler(): ActorRef = {

    // Setup command handler for assembly - note that CommandHandler connects directly to galilHCD here, not state receiver
    context.actorOf(SingleAxisCommandHandler.props(ac, galilHCD, eventPublisher, saac, aapi))

  }

  /**
   * Performs the initial validation of the incoming SetupConfigArg
   */
  override def validateSequenceConfigArg(sca: SetupConfigArg): ValidationList = {

    // Are all of the configs really for us and correctly formatted, etc?

    ConfigValidator.validateSetupConfigArg(sca)

  }

  // Gets the assembly configurations from the config service, or a resource file, if not found and
  // returns the two parsed objects.

  def getAssemblyConfigs: Future[(SingleAxisControlConfig)] = {
    // This is required by the ConfigServiceClient
    implicit val system = context.system
    import system.dispatcher

    implicit val timeout = Timeout(3.seconds)
    val f = ConfigServiceClient.getConfigFromConfigService(singleAxisConfigFile, resource = Some(resource))
    // parse the future
    f.map(configOpt => (SingleAxisControlConfig(configOpt.get)))
  }

  def generateAssemblyConfig(): SingleAxisAssemblyConfig = {

    val controlConfig = Await.result(getAssemblyConfigs, 5.seconds)
    val singleAxisAssemblyConfig = SingleAxisAssemblyConfig(controlConfig)

    singleAxisAssemblyConfig
  }

}

/**
 * All assembly messages are indicated here
 */
object SingleAxisAssembly {

  // Get the single axis assembly config file from the config service, or use the given resource file if that doesn't work
  val singleAxisConfigFile = new File("poc/singleAxis")
  val resource = new File("singleAxisAssembly.conf")

  def props(assemblyInfo: AssemblyInfo, supervisor: ActorRef) = Props(classOf[SingleAxisAssembly], assemblyInfo, supervisor)

}
