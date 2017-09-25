package org.tmt.aps.ics.hcd

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import csw.services.ts.TimeService
import csw.services.ts.TimeService._

class MotionWorker(val start: Int, val destinationIn: Int, val delayInMS: Int, replyTo: ActorRef, diagFlag: Boolean) extends Actor with ActorLogging with TimeService.TimeServiceScheduler {

  import MotionWorker._
  import TimeService._

  private[hcd] var destination = destinationIn

  private var numSteps = calcNumSteps(start, destination)
  private var stepSize = calcStepSize(start, destination, numSteps)
  private var stepCount = 0
  // Can be + or -
  private var cancelFlag = false
  private[hcd] val delayInNanoSeconds: Long = delayInMS * 1000000
  private var current = start

  override def receive: Receive = {
    case Start =>
      if (diagFlag) diag("Starting", start, numSteps)
      replyTo ! Start
      scheduleOnce(localTimeNow.plusNanos(delayInNanoSeconds), context.self, Tick(start + stepSize))
    case Tick(currentIn) =>
      replyTo ! Tick(currentIn)

      current = currentIn
      // Keep a count of steps in this MotionWorker instance
      stepCount += 1

      // If we are on the last step of a move, then distance equals 0
      val distance = calcDistance(current, destination)
      val done = distance == 0
      // To fix rounding errors, if last step set current to destination
      val last = lastStep(current, destination, stepSize)
      val nextPos = if (last) destination else currentIn + stepSize
      if (diagFlag) log.info(s"currentIn: $currentIn, distance: $distance, stepSize: $stepSize, done: $done, nextPos: $nextPos")

      if (!done && !cancelFlag) scheduleOnce(localTimeNow.plusNanos(delayInNanoSeconds), context.self, Tick(nextPos))
      else self ! End(current)
    case MoveUpdate(destinationUpdate) =>
      destination = destinationUpdate
      numSteps = calcNumSteps(current, destination)
      stepSize = calcStepSize(current, destination, numSteps)
      log.info(s"NEW dest: $destination, numSteps: $numSteps, stepSize: $stepSize")
    case Cancel =>
      if (diagFlag) log.debug("Worker received cancel")
      cancelFlag = true // Will cause to leave on next Tick
    case end @ End(finalpos) =>
      replyTo ! end
      if (diagFlag) diag("End", finalpos, numSteps)
      // When the actor has nothing else to do, it should stop
      context.stop(self)

    case x ⇒ log.error(s"Unexpected message in MotionWorker: $x")
  }

  def diag(hint: String, current: Int, stepValue: Int): Unit = log.info(s"$hint: start=$start, dest=$destination, totalSteps: $stepValue, current=$current")
}

object MotionWorker {
  def props(start: Int, destination: Int, delayInMS: Int, replyTo: ActorRef, diagFlag: Boolean) = Props(classOf[MotionWorker], start, destination, delayInMS, replyTo, diagFlag)

  trait MotionWorkerMsgs
  case object Start extends MotionWorkerMsgs
  case class End(finalpos: Int) extends MotionWorkerMsgs
  case class Tick(current: Int) extends MotionWorkerMsgs
  case class MoveUpdate(destination: Int) extends MotionWorkerMsgs
  case object Cancel extends MotionWorkerMsgs

  def calcNumSteps(start: Int, end: Int): Int = {
    val diff = Math.abs(start - end)
    if (diff <= 5) 1
    else if (diff <= 20) 6
    else if (diff <= 500) 11
    else 21

  }

  def calcStepSize(current: Int, destination: Int, steps: Int): Int = (destination - current) / steps

  //def stepNumber(stepCount: Int, numSteps: Int) = numSteps - stepCount

  def calcDistance(current: Int, destination: Int): Int = Math.abs(current - destination)
  def lastStep(current: Int, destination: Int, stepSize: Int): Boolean = calcDistance(current, destination) <= Math.abs(stepSize)

}