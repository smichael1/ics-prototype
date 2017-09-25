package org.tmt.aps.ics.hcd

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.services.ts.TimeService
import csw.services.ts.TimeService._

/**
 * This class provides a simulator of a single axis device for the purpose of testing TMT HCDs and Assemblies.
 *
 * @param axisConfig an AxisConfig object that contains a description of the axis
 * @param replyTo    an actor that will be updated with information while the axis executes
 */
class GalilAxisActor(val axisConfig: AxisConfig, replyTo: Option[ActorRef]) extends Actor with ActorLogging with TimeService.TimeServiceScheduler {

  import MotionWorker._
  import GalilAxisActor._

  // Check that the home position is not in a limit area - with this check it is not neceesary to check for limits after homing
  assert(axisConfig.home > axisConfig.lowLimit, s"home position must be greater than lowUser value: ${axisConfig.lowLimit}")
  assert(axisConfig.home < axisConfig.highLimit, s"home position must be less than highUser value: ${axisConfig.highLimit}")

  // The following are state information for the axis. These values are updated while the axis runs
  // This is safe because there is no way to change the variables other than within this actor
  // When created, the current is set to the start current
  private[hcd] var current = axisConfig.startPosition
  private[hcd] var inLowLimit = false
  private[hcd] var inHighLimit = false
  private[hcd] var inHome = false
  private[hcd] var axisState: AxisState = AXIS_IDLE

  // Statistics for status
  private[hcd] var initCount = 0
  // Number of init requests
  private[hcd] var moveCount = 0
  // Number of move requests
  private[hcd] var homeCount = 0
  // Number of home requests
  private[hcd] var limitCount = 0
  // Number of times in a limit
  private[hcd] var successCount = 0
  // Number of successful requests
  private[hcd] var failureCount = 0
  // Number of failed requests
  private[hcd] var cancelCount = 0 // Number of times a move has been cancelled

  // Set initially to the idle receive
  def receive: Receive = idleReceive

  // Short-cut to forward a messaage to the optional replyTo actor
  private def update(replyTo: Option[ActorRef], msg: AnyRef) = replyTo.foreach(_ ! msg)

  private def idleReceive: Receive = {
    case InitialState =>
      // Send the inital position for its state to the caller directly
      sender() ! getState

    case Datum =>
      axisState = AXIS_MOVING
      update(replyTo, AxisStarted)
      // Takes some time and increments the current
      scheduleOnce(localTimeNow.plusSeconds(1), context.self, DatumComplete)
      // Stats
      initCount += 1
      moveCount += 1

    case DatumComplete =>
      // Set limits
      axisState = AXIS_IDLE
      // Power on causes motion of one unit!
      current += 1
      checkLimits()
      // Stats
      successCount += 1
      // Send Update
      update(replyTo, getState)

    case GetStatistics =>
      sender() ! AxisStatistics(axisConfig.axisName, initCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount)

    case PublishAxisUpdate =>
      update(replyTo, getState)

    case Home =>
      axisState = AXIS_MOVING
      log.debug(s"AxisHome: $axisState")
      update(replyTo, AxisStarted)
      val props = MotionWorker.props(current, axisConfig.home, delayInMS = 100, self, diagFlag = false)
      val mw = context.actorOf(props, "homeWorker")
      context.become(homeReceive(mw))
      mw ! Start
      // Stats
      moveCount += 1

    case HomeComplete(finalPosition) =>
      //println("Home complete")
      axisState = AXIS_IDLE
      current = finalPosition
      // Set limits
      checkLimits()
      if (inHome) homeCount += 1
      // Stats
      successCount += 1
      // Send Update
      update(replyTo, getState)

    case Move(targetPosition, diagFlag) =>
      log.debug(s"Move: $targetPosition")
      axisState = AXIS_MOVING
      update(replyTo, AxisStarted)
      val clampedTargetPosition = GalilAxisActor.limitMove(axisConfig, targetPosition)
      // The 200 ms here is the time for one step, so a 10 step move takes 2 seconds
      val props = MotionWorker.props(current, clampedTargetPosition, delayInMS = axisConfig.stepDelayMS, self, diagFlag)
      val mw = context.actorOf(props, s"moveWorker-${System.currentTimeMillis}")
      context.become(moveReceive(mw))
      mw ! Start
      // Stats
      moveCount += 1

    case MoveComplete(finalPosition) =>
      log.debug("Move Complete")
      axisState = AXIS_IDLE
      current = finalPosition
      // Set limits
      checkLimits()
      // Do the count of limits
      if (inHighLimit || inLowLimit) limitCount += 1
      // Stats
      successCount += 1
      // Send Update
      update(replyTo, getState)

    case CancelMove =>
      log.debug("Received Cancel Move while idle :-(")
      // Stats
      cancelCount += 1

    case x => log.error(s"Unexpected message in idleReceive: $x")
  }

  // This receive is used when executing a Home command
  private def homeReceive(worker: ActorRef): Receive = {
    case Start =>
      log.debug("Home Start")
    case Tick(currentIn) =>
      current = currentIn
      // Set limits - this was a bug - need to do this after every step
      checkLimits()
      // Send Update
      update(replyTo, getState)
    case End(finalpos) =>
      context.become(idleReceive)
      self ! HomeComplete(finalpos)
    case x => log.error(s"Unexpected message in homeReceive: $x")
  }

  private def moveReceive(worker: ActorRef): Receive = {
    case Start =>
      log.debug("Move Start")
    case CancelMove =>
      log.debug("Cancel MOVE")
      worker ! Cancel
      // Stats
      cancelCount += 1
    case Move(targetPosition, _) =>
      // When this is received, we update the final position while a motion is happening
      worker ! MoveUpdate(targetPosition)
    case Tick(currentIn) =>
      current = currentIn
      log.debug("Move Update")
      // Set limits - this was a bug - need to do this after every step
      checkLimits()
      // Send Update to caller
      update(replyTo, getState)
    case End(finalpos) =>
      log.debug("Move End")
      context.become(idleReceive)
      self ! MoveComplete(finalpos)
    case x => log.error(s"Unexpected message in moveReceive: $x")
  }

  private def checkLimits(): Unit = {
    inHighLimit = isHighLimit(axisConfig, current)
    inLowLimit = isLowLimit(axisConfig, current)
    inHome = isHomed(axisConfig, current)
  }

  private def getState = AxisUpdate(axisConfig.axisName, axisState, current, inLowLimit, inHighLimit, inHome)
}

object GalilAxisActor {
  def props(axisConfig: AxisConfig, replyTo: Option[ActorRef] = None) = Props(classOf[GalilAxisActor], axisConfig, replyTo)

  //  case class AxisConfig(axisName: String, lowLimit: Int, lowUser: Int, highUser: Int, highLimit: Int, home: Int, startPosition: Int, stepDelayMS: Int)

  trait AxisState
  case object AXIS_IDLE extends AxisState
  case object AXIS_MOVING extends AxisState
  case object AXIS_ERROR extends AxisState

  trait AxisRequest
  case object Home extends AxisRequest
  // sm- this may not be necessary
  case object Datum extends AxisRequest

  case class Move(position: Int, diagFlag: Boolean = false) extends AxisRequest
  case object CancelMove extends AxisRequest

  // sm - this may not be necessary
  case object GetStatistics extends AxisRequest

  case object PublishAxisUpdate extends AxisRequest

  trait AxisResponse
  case object AxisStarted extends AxisResponse
  case object AxisFinished extends AxisResponse
  case class AxisUpdate(axisName: String, state: AxisState, current: Int, inLowLimit: Boolean, inHighLimit: Boolean, inHomed: Boolean) extends AxisResponse
  case class AxisFailure(reason: String) extends AxisResponse

  // sm - this may not be necessary
  case class AxisStatistics(axisName: String, initCount: Int, moveCount: Int, homeCount: Int, limitCount: Int, successCount: Int, failureCount: Int, cancelCount: Int) extends AxisResponse {
    override def toString = s"name: $axisName, inits: $initCount, moves: $moveCount, homes: $homeCount, limits: $limitCount, success: $successCount, fails: $failureCount, cancels: $cancelCount"
  }

  // Internal
  trait InternalMessages
  case object DatumComplete extends InternalMessages
  case class HomeComplete(position: Int) extends InternalMessages
  case class MoveComplete(position: Int) extends InternalMessages
  case object InitialState extends InternalMessages
  case object InitialStatistics extends InternalMessages

  // Helper functions in object for testing
  // limitMove clamps the request value to the hard limits
  def limitMove(ac: AxisConfig, request: Int): Int = Math.max(Math.min(request, ac.highLimit), ac.lowLimit)

  // Check to see if position is in the "limit" zones
  def isHighLimit(ac: AxisConfig, current: Int): Boolean = current >= ac.highLimit

  def isLowLimit(ac: AxisConfig, current: Int): Boolean = current <= ac.lowLimit

  def isHomed(ac: AxisConfig, current: Int): Boolean = current == ac.home

}

