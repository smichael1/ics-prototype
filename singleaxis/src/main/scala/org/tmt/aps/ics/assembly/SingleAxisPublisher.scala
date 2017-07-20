package org.tmt.aps.ics.assembly

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import org.tmt.aps.ics.assembly.SingleAxisPublisher.{AxisStateUpdate, AxisStatsUpdate, EngrUpdate}
import csw.services.events.{EventService, TelemetryService}
import csw.services.loc.LocationService.{Location, ResolvedTcpLocation, Unresolved}
import csw.services.loc.LocationSubscriberClient
import csw.util.config._
import csw.util.config.Events.{StatusEvent, SystemEvent}
import org.tmt.aps.ics.assembly.SingleAxisStateActor.SingleAxisState

import scala.util.Failure

/**
 * An actor that provides the publishing interface to the TMT Event Service and Telemetry Service.
 *
 * The SingleAxisPublisher receives messages from other actors that need to publish an event of some kind. The messages are
 * repackaged as SystemEvents or StatusEvents as needed.
 *
 * Currently, this actor publishes the engr StatusEvent and the state StatusEvent.  The engr
 * StatusEvent is triggered by the arrival of an EngrUpdate message, and the state StatusEvent is triggered by the
 * SingleAxisState message.
 *
 * The publisher also publishes diagnostic data from the TelemetryGenerator as an axis state and statistics StatusEvent.
 *
 * Values in received messages are assumed to be correct and ready for publishing.
 *
 * @param assemblyContext the AssemblyContext contains important shared values and useful functions
 * @param eventServiceIn optional EventService for testing event service
 * @param telemetryServiceIn optional Telemetryservice for testing with telemetry service
 */
class SingleAxisPublisher(assemblyContext: AssemblyContext, eventServiceIn: Option[EventService], telemetryServiceIn: Option[TelemetryService]) extends Actor with ActorLogging with LocationSubscriberClient {
  import assemblyContext._

  // Needed for future onFailure
  import context.dispatcher

  implicit val system: ActorSystem = context.system

  log.info("Event Service in: " + eventServiceIn)
  log.info("Telemetry Service in: " + telemetryServiceIn)

  def receive: Receive = publishingEnabled(eventServiceIn, telemetryServiceIn)

  def publishingEnabled(eventService: Option[EventService], telemetryService: Option[TelemetryService]): Receive = {

    case EngrUpdate(rtcFocusError, stagePosition, zenithAngle) =>
      publishEngr(telemetryService, rtcFocusError, stagePosition, zenithAngle)

    case ts: SingleAxisState =>
      publishState(telemetryService, ts)

    case AxisStateUpdate(axisName, position, state, inLowLimit, inHighLimit, inHome, stagePosition) =>
      log.debug("SingleAxisPublisher:: AxisStateUpdate received")
      publishAxisState(telemetryService, axisName, position, state, inLowLimit, inHighLimit, inHome, stagePosition)

    case AxisStatsUpdate(axisName, datumCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount) =>
      log.debug("SingleAxisPublisher:: AxisStatsUpdate received")
      publishAxisStats(telemetryService, axisName, datumCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount)

    case location: Location =>
      handleLocations(location, eventService, telemetryService)

    case x => log.error(s"Unexpected message in SingleAxisPublisher:publishingEnabled: $x")
  }

  def handleLocations(location: Location, currentEventService: Option[EventService], currentTelemetryService: Option[TelemetryService]): Unit = {
    location match {
      case t: ResolvedTcpLocation =>
        log.debug(s"Received TCP Location: ${t.connection}")
        // Verify that it is the event service
        if (t.connection == EventService.eventServiceConnection()) {
          val newEventService = Some(EventService.get(t.host, t.port))
          log.debug(s"Event Service at: $newEventService")
          context.become(publishingEnabled(newEventService, currentTelemetryService))
        }
        if (t.connection == TelemetryService.telemetryServiceConnection()) {
          val newTelemetryService = Some(TelemetryService.get(t.host, t.port))
          log.debug(s"Telemetry Service at: $newTelemetryService")
          context.become(publishingEnabled(currentEventService, newTelemetryService))
        }
      case u: Unresolved =>
        log.debug(s"Unresolved: ${u.connection}")
        if (u.connection == EventService.eventServiceConnection()) context.become(publishingEnabled(None, currentTelemetryService))
        else if (u.connection == TelemetryService.telemetryServiceConnection()) context.become(publishingEnabled(currentEventService, None))
      case default =>
        log.info(s"SingleAxisPublisher received some other location: $default")
    }
  }

  private def publishEngr(telemetryService: Option[TelemetryService], rtcFocusError: DoubleItem, stagePosition: DoubleItem, zenithAngle: DoubleItem) = {
    val ste = StatusEvent(compHelper.engStatusEventPrefix).madd(rtcFocusError, stagePosition, zenithAngle)
    log.info(s"Status publish of $compHelper.engStatusEventPrefix: $ste")
    telemetryService.foreach(_.publish(ste).onComplete {
      case Failure(ex) => log.error(s"TrombonePublisher failed to publish engr event: $ste", ex)
      case _           =>
    })
  }

  private def publishState(telemetryService: Option[TelemetryService], ts: SingleAxisState) = {
    // We can do this for convenience rather than using TromboneStateHandler's stateReceive
    val ste = StatusEvent(compHelper.singleAxisStateStatusEventPrefix).madd(ts.cmd, ts.move)
    log.info(s"Status state publish of $compHelper.singleAxisStateStatusEventPrefix: $ste")
    telemetryService.foreach(_.publish(ste).onComplete {
      case Failure(ex) => log.error(s"TrombonePublisher failed to publish single axis state: $ste", ex)
      case _           =>
    })
  }

  private def publishAxisState(telemetryService: Option[TelemetryService], axisName: StringItem, position: IntItem, state: ChoiceItem, inLowLimit: BooleanItem, inHighLimit: BooleanItem, inHome: BooleanItem, stagePosition: DoubleItem) = {
    val ste = StatusEvent(compHelper.axisStateEventPrefix).madd(axisName, position, state, inLowLimit, inHighLimit, inHome, stagePosition)
    log.debug(s"Axis state publish of $compHelper.axisStateEventPrefix: $ste")
    telemetryService.foreach(_.publish(ste).onComplete {
      case Failure(ex) => log.error(s"TrombonePublisher failed to publish single axis state: $ste", ex)
      case _           =>
    })
  }

  def publishAxisStats(telemetryService: Option[TelemetryService], axisName: StringItem, datumCount: IntItem, moveCount: IntItem, homeCount: IntItem, limitCount: IntItem, successCount: IntItem, failureCount: IntItem, cancelCount: IntItem): Unit = {
    val ste = StatusEvent(assemblyContext.compHelper.axisStatsEventPrefix).madd(axisName, datumCount, moveCount, homeCount, limitCount, successCount, failureCount, cancelCount)
    log.debug(s"Axis stats publish of $assemblyContext.compHelper.axisStatsEventPrefix: $ste")
    telemetryService.foreach(_.publish(ste).onComplete {
      case Failure(ex) => log.error(s"TrombonePublisher failed to publish trombone axis stats: $ste", ex)
      case _           =>
    })
  }

}

object SingleAxisPublisher {
  def props(assemblyContext: AssemblyContext, eventService: Option[EventService] = None, telemetryService: Option[TelemetryService] = None) =
    Props(classOf[SingleAxisPublisher], assemblyContext, eventService, telemetryService)

  /**
   * Used by actors wishing to cause an engineering event update
   * @param focusError focus error value as DoubleItem
   * @param stagePosition stage position as a DoubleItem
   * @param zenithAngle zenith angle update as a DoubleItem
   */
  case class EngrUpdate(focusError: DoubleItem, stagePosition: DoubleItem, zenithAngle: DoubleItem)

  case class AxisStateUpdate(axisName: StringItem, position: IntItem, state: ChoiceItem, inLowLimit: BooleanItem, inHighLimit: BooleanItem, inHome: BooleanItem, stagePosition: DoubleItem)

  case class AxisStatsUpdate(axisName: StringItem, initCount: IntItem, moveCount: IntItem, homeCount: IntItem, limitCount: IntItem, successCount: IntItem, failCount: IntItem, cancelCount: IntItem)

  case class AssemblyStateUpdate(assemblyName: StringItem, cmdState: ChoiceItem, moveState: ChoiceItem)
}