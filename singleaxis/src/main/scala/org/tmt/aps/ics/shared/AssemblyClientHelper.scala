package org.tmt.aps.ics.shared

import akka.util.Timeout
import com.typesafe.scalalogging.LazyLogging
import csw.services.ccs.AssemblyMessages.{DiagnosticMode, OperationsMode}
import csw.util.config.{BooleanKey, Configurations, DoubleItem, DoubleKey}
import csw.util.config.Configurations.{ConfigKey, SetupConfig, SetupConfigArg}
import csw.services.ccs.BlockingAssemblyClient
import csw.services.ccs.CommandStatus.CommandResult
import csw.services.events.EventService.EventMonitor
import csw.services.events.TelemetryService.TelemetryMonitor
import csw.services.events.{Event, EventService, TelemetryService}
import csw.util.config.UnitsOfMeasure.{degrees, kilometers, millimeters, meters}


import scala.concurrent.duration._

object AssemblyClientHelper extends LazyLogging {

  import csw.services.sequencer.SequencerEnv._

  /**
   * Generic function to print any event
   */
  val evPrinter = (ev: Event) => { println(s"EventReceived: $ev") }

  /**
   * Subscribe to all StatusEvents published by the SingleAxisAssembly and print them to screen
   * @param ts a Telemetry Service reference
   * @return an EventService reference
   */
  def getStatusEvents(ts: TelemetryService, componentPrefix: String): TelemetryMonitor = ts.subscribe(evPrinter, true, s"$componentPrefix.*")

  /**
   * Subscribe to all SystemEvents published by SingleAxisAssembly and print them to the screen
   * @param es an EventService reference
   * @return EventMonitor
   */
  def getSystemEvents(es: EventService, componentPrefix: String): EventMonitor = es.subscribe(evPrinter, false, s"$componentPrefix.*")

  /**
   * Puts the Single Axis Assembly into diagnostic mode
   * @param tla the BlockingAssemblyClient returned by getSingleAxis
   */
  def diagnosticMode(tla: BlockingAssemblyClient): Unit = {
    tla.client.assemblyController ! DiagnosticMode
  }

  /**
   * Puts the SingleAxis Assembly into operations mode
   * @param tla the BlockingAssemblyClient returned by getSingleAxis
   */
  def operationsMode(tla: BlockingAssemblyClient): Unit = {
    tla.client.assemblyController ! OperationsMode
  }

}