package org.tmt.aps.ics.assembly

import akka.actor.{Actor, ActorRef, ActorLogging, Props}
import csw.util.config._

/**
 * Note that this state actor is not a listener for events. Only the client listens.
 */
class SingleAxisStateActor(publisher: ActorRef) extends Actor with ActorLogging {

  import SingleAxisStateActor._

  // This actor subscribes to SingleAxisState using the EventBus
  //context.system.eventStream.subscribe(self, classOf[SingleAxisState])

  def receive = stateReceive(SingleAxisState(cmdDefault, moveDefault))

  publisher ! SingleAxisState(cmdDefault, moveDefault) // publish initial state

  /**
   * This stateReceive must be added to the actor's receive chain.
   * This is called when some other actor changes the state to ensure the value is updated for all users
   *
   * @return Akka Receive partial function
   */
  def stateReceive(currentState: SingleAxisState): Receive = {

    case SetState(ts) =>
      if (ts != currentState) {
        context.system.eventStream.publish(ts)
        context.become(stateReceive(ts))
        publisher ! ts // publish the assembly state change to the world
        sender() ! StateWasSet(true)
      } else {
        sender() ! StateWasSet(false)
      }
    case GetState =>
      sender() ! currentState

    case x => log.error(s"SingleAxisStateActor received an unexpected message: $x")
  }

}

trait SingleAxisStateClient {
  this: Actor => // this line says that SingleAxisStateClient cannot be mixed-in to a concrete class that does not extend Actor

  import SingleAxisStateActor._

  // This actor subscribes to SingleAxisState using the EventBus
  context.system.eventStream.subscribe(self, classOf[SingleAxisState])

  private var internalState = defaultSingleAxisState

  def stateReceive: Receive = {
    case ts: SingleAxisState =>
      internalState = ts
  }

  /**
   * The currentState as a SingleAxisState is returned.
   * @return SingleAxisState current state
   */
  def currentState: SingleAxisState = internalState
}

object SingleAxisStateActor {

  def props(telemetryGenerator: ActorRef): Props = Props(new SingleAxisStateActor(telemetryGenerator))

  // Keys for state telemetry item
  val cmdUninitialized = Choice("uninitialized")
  val cmdReady = Choice("ready")
  val cmdBusy = Choice("busy")
  val cmdContinuous = Choice("continuous")
  val cmdError = Choice("error")
  val cmdKey = ChoiceKey("cmd", cmdUninitialized, cmdReady, cmdBusy, cmdContinuous, cmdError)
  val cmdDefault = cmdItem(cmdUninitialized)
  def cmd(ts: SingleAxisState): Choice = ts.cmd.head // ts.cmd is a ChoiceItem which extends Item that defines 'head' as the first element of the values vector

  /**
   * A convenience method to set the cmdItem choice
   * @param ch one of the cmd choices
   * @return a ChoiceItem with the choice value
   */
  def cmdItem(ch: Choice): ChoiceItem = cmdKey.set(ch)

  val moveUnindexed = Choice("unindexed")
  val moveIndexing = Choice("indexing")
  val moveIndexed = Choice("indexed")
  val moveMoving = Choice("moving")
  val moveKey = ChoiceKey("move", moveUnindexed, moveIndexing, moveIndexed, moveMoving)
  val moveDefault = moveItem(moveUnindexed)
  def move(ts: SingleAxisState): Choice = ts.move.head

  /**
   * A convenience method to set the moveItem choice
   * @param ch one of the move choices
   * @return a ChoiceItem with the choice value
   */
  def moveItem(ch: Choice): ChoiceItem = moveKey.set(ch)

  /**
   * Method determining if a command can be accepted
   * @param ts state
   * @return true if a command can be accepted in this state
   */
  def canAcceptCmd(ts: SingleAxisState): Boolean = cmd(ts) == cmdReady || cmd(ts) == cmdBusy || cmd(ts) == cmdContinuous || cmd(ts) == cmdError

  /**
   * Method determining if a position command can be accepted
   * @param ts state
   * @return true if a position command can be accepted in this state
   */
  def canAcceptPositionCmd(ts: SingleAxisState): Boolean = (canAcceptCmd(ts) && ((move(ts) == moveIndexed || move(ts) == moveMoving)))

  val defaultSingleAxisState = SingleAxisState(cmdDefault, moveDefault)

  /**
   * This class is sent to the publisher for publishing when any state value changes
   *
   * @param cmd         the current cmd state
   * @param move        the current move state
   */
  case class SingleAxisState(cmd: ChoiceItem, move: ChoiceItem)

  /**
   * Update the current state with a SingleAxisState
   * @param singleAxisState the new single axis state value
   */
  case class SetState(singleAxisState: SingleAxisState)

  object SetState {
    /**
     * Alternate way to create the SetState message using items
     * @param cmd a ChoiceItem created with cmdItem
     * @param move a ChoiceItem created with moveItem
     * @return a new SetState message instance
     */
    def apply(cmd: ChoiceItem, move: ChoiceItem): SetState = SetState(SingleAxisState(cmd, move))

    /**
     * Alternate way to create the SetState message using primitives
     * @param cmd a Choice for the cmd value (i.e. cmdReady, cmdBusy, etc.)
     * @param move a Choice for the move value (i.e. moveUnindexed, moveIndexing, etc.)
     * @return a new SetState message instance
     */
    def apply(cmd: Choice, move: Choice): SetState = SetState(SingleAxisState(cmdItem(cmd), moveItem(move)))
  }

  /**
   * A message that causes the current state to be sent back to the sender
   */
  case object GetState

  /**
   * Reply to the SetState message that indicates if the state was actually set (only if different than current state)
   */
  case class StateWasSet(wasSet: Boolean)
}
