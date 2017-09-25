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
import org.tmt.aps.ics.assembly.motion.MotionTelemetryGenerationHandler

import org.tmt.aps.ics.assembly.motion.MultiAxisMotionAssemblyApi // FIXME - should not be here

//import scala.concurrent._

/**
 * Top Level Actor for Multi Axis Assembly
 *
 * MultiAxisAssembly starts up the component doing the following:
 * creating all needed actors,
 * handling initialization,
 * participating in lifecycle with Supervisor,
 * handles locations for distribution throughout component
 * receives comamnds and forwards them to the CommandHandler by extending the AssemblyController
 */
class MultiAxisAssembly(info: AssemblyInfo, supervisor: ActorRef) extends AkkaAssembly(info, supervisor) {

  import MultiAxisAssembly._

  implicit val maac: MultiAxisAssemblyConfig = generateAssemblyConfig()
  implicit val aapi = MultiAxisMotionAssemblyApi(info.prefix)

  log.info(s"MultiAxisAssembly (${info.prefix}) startup")
  init

  def init() = {
    super.initialize()
  }

  def initTelemetryGenerator(): ActorRef = {

    // This sets up the diagnostic data publisher - setting Var here
    context.actorOf(TelemetryGenerator.props(ac, galilHCD, Some(eventPublisher), new MotionTelemetryGenerationHandler(maac)))

  }

  def initCommandHandler(): ActorRef = {

    // Setup command handler for assembly - note that CommandHandler connects directly to galilHCD here, not state receiver
    context.actorOf(SingleAxisCommandHandler.props(ac, galilHCD, eventPublisher, maac, aapi))

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

  def getAssemblyConfigs: Future[(MultiAxisAssemblyConfig)] = {
    // This is required by the ConfigServiceClient
    implicit val system = context.system
    import system.dispatcher

    implicit val timeout = Timeout(3.seconds)

    // config file and fallback resource is based on the deployment component name
    val multiAxisConfigFile = new File(s"ics/${info.componentName}")
    val resource = new File(s"${info.componentName}.conf")

    val f = ConfigServiceClient.getConfigFromConfigService(multiAxisConfigFile, resource = Some(resource))

    // parse the future
    f.map(configOpt => (MultiAxisAssemblyConfig(configOpt.get)))

  }

  def generateAssemblyConfig(): MultiAxisAssemblyConfig = {

    val multiAxisConfig = Await.result(getAssemblyConfigs, 5.seconds)
    multiAxisConfig
  }

}

/**
 *
 * All assembly messages are indicated here
 */
object MultiAxisAssembly {

  def props(assemblyInfo: AssemblyInfo, supervisor: ActorRef) = Props(classOf[MultiAxisAssembly], assemblyInfo, supervisor)

}
