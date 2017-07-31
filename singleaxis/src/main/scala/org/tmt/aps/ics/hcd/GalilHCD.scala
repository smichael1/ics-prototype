package org.tmt.aps.ics.hcd

import java.io.File

import akka.actor.{ActorRef, Props}
import akka.pattern.ask
import akka.util.Timeout
import csw.services.ccs.HcdController
import csw.services.cs.akka.ConfigServiceClient
import csw.services.loc.ComponentType
import csw.services.pkg.Component.HcdInfo
import csw.services.pkg.Supervisor._
import csw.services.pkg.Hcd
import csw.util.config.Configurations.{ConfigKey, SetupConfig}
import csw.util.config.StateVariable.CurrentState
import csw.util.config.UnitsOfMeasure.encoder
import csw.util.config._

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.implicitConversions

/**
 * GalilHCD -- This is the Top Level Actor for the GalilHCD
 * It's main responsibilties are to interact with the Galil HCD axis (a simulator)
 * It also:
 * - initializes itself from Configiuration Service
 * - Works with the Supervisor to implement the lifecycle
 * - Handles incoming commands
 * - Generates CurrentState for the Assembly
 */
class GalilHCD(override val info: HcdInfo, supervisor: ActorRef) extends Hcd with HcdController {

  import context.dispatcher
  import SingleAxisSimulator._
  import GalilHCD._

  implicit val timeout = Timeout(2.seconds)

  log.info("initializing HCD")

  // Note: The following could be done with futures and passed as parameters to context.become(), but are declared as
  // vars here so that they can be examined by test cases

  // Initialize values -- This causes an update to the listener
  // The current axis position from the hardware axis, initialize to default value
  var current: AxisUpdate = _
  var stats: AxisStatistics = _

  // Create an axis for simulating axis motion
  var stageAxis: ActorRef = _

  // Get the axis config file from the config service, then use it to start the stageAxis actor
  // and get the current values. Once that is done, we can tell the supervisor actor that we are ready
  // and then wait for the Running message from the supervisor before going to the running state.
  // Initialize axis from ConfigService
  var axisConfig: AxisConfig = _

  try {
    // The following line could fail, if the config service is not running or the file is not found
    axisConfig = Await.result(getAxisConfig, timeout.duration)

    // Create an axis for simulating axis motion
    stageAxis = setupAxis(axisConfig)

    // Initialize values -- This causes an update to the listener
    // The current axis position from the hardware axis, initialize to default value

    current = Await.result((stageAxis ? InitialState).mapTo[AxisUpdate], timeout.duration)

    stats = Await.result((stageAxis ? GetStatistics).mapTo[AxisStatistics], timeout.duration)

    // Required setup for Lifecycle in order to get messages
    supervisor ! Initialized
    // 0.4    supervisor ! Started
  } catch {
    case ex: Exception => {
      supervisor ! InitializeFailure(ex.getMessage)
      log.info("We got an exception: " + ex.getMessage)
    }
  }

  // Receive actor methods
  override def receive: Receive = initializingReceive

  // State where we are waiting for the Running message from the supervisor
  private def initializingReceive: Receive = publisherReceive orElse {
    case Running =>
      // When Running is received, transition to running Receive
      log.debug("received Running")
      context.become(runningReceive)

    case ShutdownComplete => log.info(s"${info.componentName} shutdown complete")

    case x                => log.error(s"Unexpected message in GalilHCD (Not running yet): $x")
  }

  // Receive partial function to handle runtime lifecycle meessages
  private def runningReceive: Receive = controllerReceive orElse {
    case Running =>
      log.info("Received running")

    case RunningOffline =>
      log.info("Received running offline")

    case DoRestart =>
      log.info("Received dorestart")

    case DoShutdown =>
      log.info("Received doshutdown")
      // Just say complete for now
      supervisor ! ShutdownComplete

    case LifecycleFailureInfo(state: LifecycleState, reason: String) =>
      log.info(s"Received failed state: $state for reason: $reason")

    case GetAxisStats =>
      stageAxis ! GetStatistics

    case GetAxisUpdate =>
      stageAxis ! PublishAxisUpdate

    case GetAxisUpdateNow =>
      sender() ! current

    case AxisStarted =>
    //println("Axis Started")

    case GetAxisConfig =>
      val axisConfigState = defaultConfigState.madd(
        lowLimitKey -> axisConfig.lowLimit,
        lowUserKey -> axisConfig.lowUser,
        highUserKey -> axisConfig.highUser,
        highLimitKey -> axisConfig.highLimit,
        homeValueKey -> axisConfig.home,
        startValueKey -> axisConfig.startPosition,
        stepDelayMSKey -> axisConfig.stepDelayMS
      )
      notifySubscribers(axisConfigState)

    case au @ AxisUpdate(_, axisState, currentPosition, inLowLimit, inHighLimit, inHomed) =>
      // Update actor state
      this.current = au
      //      context.become(runningReceive)
      val stageAxisState = defaultAxisState.madd(
        positionKey -> currentPosition withUnits encoder,
        stateKey -> axisState.toString,
        inLowLimitKey -> inLowLimit,
        inHighLimitKey -> inHighLimit,
        inHomeKey -> inHomed
      )
      notifySubscribers(stageAxisState)

    case as: AxisStatistics =>
      log.debug(s"AxisStatus: $as")
      // Update actor statistics
      this.stats = as
      //      context.become(runningReceive)
      val stageStats = defaultStatsState.madd(
        datumCountKey -> as.initCount,
        moveCountKey -> as.moveCount,
        limitCountKey -> as.limitCount,
        homeCountKey -> as.homeCount,
        successCountKey -> as.successCount,
        failureCountKey -> as.failureCount,
        cancelCountKey -> as.cancelCount
      )
      notifySubscribers(stageStats)

    case ShutdownComplete => log.info(s"${info.componentName} shutdown complete")

    case x                => log.error(s"Unexpected message in GalilHCD (running state): $x")
  }

  protected def process(sc: SetupConfig): Unit = {
    import GalilHCD._
    log.debug(s"Stage Axis process received sc: $sc")

    sc.configKey match {
      case `axisMoveCK` =>
        stageAxis ! Move(sc(positionKey).head, diagFlag = true)
      case `axisDatumCK` =>
        log.info("Received Datum")
        stageAxis ! Datum
      case `axisHomeCK` =>
        stageAxis ! Home
      case `axisCancelCK` =>
        stageAxis ! CancelMove
      case x => log.warning(s"Unknown config key $x")

    }
  }

  private def setupAxis(ac: AxisConfig): ActorRef = context.actorOf(SingleAxisSimulator.props(ac, Some(self)), "Test1")

  // Utility functions
  private def getAxisConfig: Future[AxisConfig] = {
    // This is required by the ConfigServiceClient
    implicit val system = context.system

    val f = ConfigServiceClient.getConfigFromConfigService(galilConfigFile, resource = Some(resource))

    f.map(_.map(AxisConfig(_)).get)
  }
}

object GalilHCD {
  def props(hcdInfo: HcdInfo, supervisor: ActorRef) = Props(classOf[GalilHCD], hcdInfo, supervisor)

  // Get the galil config file from the config service, or use the given resource file if that doesn't work
  val galilConfigFile = new File("poc/galilHCD")
  val resource = new File("galilHCD.conf")

  // HCD Info
  val componentName = "icsGalilHCD"
  val componentType = ComponentType.HCD
  val componentClassName = "org.tmt.aps.ics.hcd.GalilHCD"
  val galilPrefix = "org.tmt.aps.ics.hcd.galilHcd"

  val galilAxisName = "stageAxis"

  val axisStatePrefix = s"$galilPrefix.axis1State"
  val axisStateCK: ConfigKey = axisStatePrefix
  val axisNameKey = StringKey("axisName")
  val AXIS_IDLE = Choice(SingleAxisSimulator.AXIS_IDLE.toString)
  val AXIS_MOVING = Choice(SingleAxisSimulator.AXIS_MOVING.toString)
  val AXIS_ERROR = Choice(SingleAxisSimulator.AXIS_ERROR.toString)
  val stateKey = ChoiceKey("axisState", AXIS_IDLE, AXIS_MOVING, AXIS_ERROR)
  val positionKey = IntKey("position")
  val positionUnits: encoder.type = encoder
  val inLowLimitKey = BooleanKey("lowLimit")
  val inHighLimitKey = BooleanKey("highLimit")
  val inHomeKey = BooleanKey("homed")

  private val defaultAxisState = CurrentState(axisStateCK).madd(
    axisNameKey -> galilAxisName,
    stateKey -> AXIS_IDLE,
    positionKey -> 0 withUnits encoder,
    inLowLimitKey -> false,
    inHighLimitKey -> false,
    inHomeKey -> false
  )

  val axisStatsPrefix = s"$galilPrefix.axisStats"
  val axisStatsCK: ConfigKey = axisStatsPrefix
  val datumCountKey = IntKey("initCount")
  val moveCountKey = IntKey("moveCount")
  val homeCountKey = IntKey("homeCount")
  val limitCountKey = IntKey("limitCount")
  val successCountKey = IntKey("successCount")
  val failureCountKey = IntKey("failureCount")
  val cancelCountKey = IntKey("cancelCount")
  private val defaultStatsState = CurrentState(axisStatsCK).madd(
    axisNameKey -> galilAxisName,
    datumCountKey -> 0,
    moveCountKey -> 0,
    homeCountKey -> 0,
    limitCountKey -> 0,
    successCountKey -> 0,
    failureCountKey -> 0,
    cancelCountKey -> 0
  )

  val axisConfigPrefix = s"$galilPrefix.axisConfig"
  val axisConfigCK: ConfigKey = axisConfigPrefix
  // axisNameKey
  val lowLimitKey = IntKey("lowLimit")
  val lowUserKey = IntKey("lowUser")
  val highUserKey = IntKey("highUser")
  val highLimitKey = IntKey("highLimit")
  val homeValueKey = IntKey("homeValue")
  val startValueKey = IntKey("startValue")
  val stepDelayMSKey = IntKey("stepDelayMS")
  // No full default current state because it is determined at runtime
  private val defaultConfigState = CurrentState(axisConfigCK).madd(
    axisNameKey -> galilAxisName
  )

  val axisMovePrefix = s"$galilPrefix.move"
  val axisMoveCK: ConfigKey = axisMovePrefix

  def positionSC(value: Int): SetupConfig = SetupConfig(axisMoveCK).add(positionKey -> value withUnits encoder)

  val axisDatumPrefix = s"$galilPrefix.datum"
  val axisDatumCK: ConfigKey = axisDatumPrefix
  val datumSC = SetupConfig(axisDatumCK)

  val axisHomePrefix = s"$galilPrefix.home"
  val axisHomeCK: ConfigKey = axisHomePrefix
  val homeSC = SetupConfig(axisHomeCK)

  val axisCancelPrefix = s"$galilPrefix.cancel"
  val axisCancelCK: ConfigKey = axisCancelPrefix
  val cancelSC = SetupConfig(axisCancelCK)

  // Testing messages for GalilHCD
  trait GalilEngineering

  case object GetAxisStats extends GalilEngineering

  /**
   * Returns an AxisUpdate through subscribers
   */
  case object GetAxisUpdate extends GalilEngineering

  /**
   * Directly returns an AxisUpdate to sender
   */
  case object GetAxisUpdateNow extends GalilEngineering

  case object GetAxisConfig extends GalilEngineering

}
