package org.tmt.aps.ics.seq

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

/**
 * TMT Source Code: 12/4/16.
 */
object Demo extends App with LazyLogging {
  import csw.services.sequencer.SequencerEnv._

  implicit val timeout = Timeout(10.seconds)

  val taName = "singleAxis"
  val thName = "icsGalilHCD"

  val componentPrefix: String = "org.tmt.aps.ics.singleAxis"

  val obsId: String = "testObsId"

  // Public command configurations
  // Init submit command
  val initPrefix = s"$componentPrefix.init"
  val initCK: ConfigKey = initPrefix

  // Datum submit command
  val datumPrefix = s"$componentPrefix.datum"
  val datumCK: ConfigKey = datumPrefix

  val stimulusSourceXKey = DoubleKey("stimulusSourceX")
  val stimulusSourceYKey = DoubleKey("stimulusSourceY")
  val stimulusSourceZKey = DoubleKey("stimulusSourceZ")

  val naRangeDistanceKey = DoubleKey("rangeDistance")
  val naRangeDistanceUnits = kilometers

  val naElevationKey = DoubleKey("elevation")
  val naElevationUnits = kilometers
  def naElevation(elevation: Double): DoubleItem = naElevationKey -> elevation withUnits naElevationUnits

  val stagePositionKey = DoubleKey("stagePosition")
  val stagePositionUnits = millimeters

  val zenithAngleKey = DoubleKey("zenithAngle")
  val zenithAngleUnits = degrees
  def za(angle: Double): DoubleItem = zenithAngleKey -> angle withUnits zenithAngleUnits

  // Move submit command
  val movePrefix = s"$componentPrefix.move"
  val moveCK: ConfigKey = movePrefix

  def moveSC(position: Double): SetupConfig = SetupConfig(moveCK).add(stagePositionKey -> position withUnits stagePositionUnits)

  // Position submit command
  val positionPrefix = s"$componentPrefix.position"
  val positionCK: ConfigKey = positionPrefix
  def positionSC(rangeDistance: Double): SetupConfig = SetupConfig(positionCK).add(naRangeDistanceKey -> rangeDistance withUnits naRangeDistanceUnits)

  // Position stimulus 
  val positionStimulusSourcePrefix = s"$componentPrefix.positionStimulusSource"
  val positionStimulusSourceCk: ConfigKey = positionStimulusSourcePrefix

  def commandStimulusPostion(commandX: Boolean, deltaX: Double, commandY: Boolean, deltaY: Double, commandZ: Boolean, deltaZ: Double): SetupConfig = {

    val sc: SetupConfig = SetupConfig(positionStimulusSourceCk)
    if (commandX) sc.add(stimulusSourceXKey -> deltaX withUnits meters)
    if (commandY) sc.add(stimulusSourceYKey -> deltaY withUnits meters)
    if (commandZ) sc.add(stimulusSourceZKey -> deltaZ withUnits degrees)
    sc
  }

  // Stop Command
  val stopPrefix = s"$componentPrefix.stop"
  val stopCK: ConfigKey = stopPrefix

  println("got to 1")

  /**
   * Generic function to print any event
   */
  val evPrinter = (ev: Event) => { println(s"EventReceived: $ev") }

  // Test SetupConfigArgs
  // Init and Datum axis
  val sca1 = Configurations.createSetupConfigArg(obsId, SetupConfig(initCK), SetupConfig(datumCK))

  // Sends One Move
  val sca2 = Configurations.createSetupConfigArg(obsId, positionSC(100.0))

  // This will send a config arg with 10 position commands
  val testRangeDistance = 40 to 130 by 10
  val positionConfigs = testRangeDistance.map(f => positionSC(f))
  val sca3 = Configurations.createSetupConfigArg(obsId, positionConfigs: _*)

  /**
   * Returns the SingleAxisAssembly after LocationService lookup
   * @return BlockingAssemblyClient
   */
  def getSingleAxis: BlockingAssemblyClient = resolveAssembly(taName)

  /**
   * Returns the GalilHCD after Location Service lookup
   * @return HcdClient
   */
  def getGalilHcd: HcdClient = resolveHcd(thName)

  /**
   * Send one position command to the SingleAxis Assembly
   * @param tla the BlockingAssemblyClient returned by getSingleAxis
   * @param pos some position as a double.  Should be around 90-200 or you will drive it to a limit
   * @return CommandResult and the conclusion of execution
   */
  def onePos(tla: BlockingAssemblyClient, pos: Double): CommandResult = {
    tla.submit(Configurations.createSetupConfigArg(obsId, positionSC(pos)))
  }

  // actually do something

  val assemblyClient = getSingleAxis

  val commandResult = onePos(assemblyClient, 60.0)

  /**
   * Subscribe to all StatusEvents published by the SingleAxisAssembly and print them to screen
   * @param ts a Telemetry Service reference
   * @return an EventService reference
   */
  def getStatusEvents(ts: TelemetryService): TelemetryMonitor = ts.subscribe(evPrinter, false, s"$componentPrefix.*")

  /**
   * Subscribe to all SystemEvents published by SingleAxisAssembly and print them to the screen
   * @param es an EventService reference
   * @return EventMonitor
   */
  def getSystemEvents(es: EventService): EventMonitor = es.subscribe(evPrinter, false, s"$componentPrefix.*")

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
