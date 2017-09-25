package org.tmt.aps.ics.assembly.motion

import org.tmt.aps.ics.assembly.SingleAxisPublisher.{AxisStateUpdate}
import csw.util.config.StateVariable.CurrentState
import org.tmt.aps.ics.assembly.Converter._
import csw.util.config.UnitsOfMeasure.{millimeters}
import csw.util.config.{DoubleKey, StringKey}
import org.tmt.aps.ics.assembly.MultiAxisAssemblyConfig
import org.tmt.aps.ics.assembly.TelemetryGeneratorHandler

/**
 *
 * @param assemblyContext      the assembly context provides overall assembly information and convenience functions
 * @param galilHCDIn        initial actorRef of the galilHCD as a [[scala.Option]]
 * @param eventPublisher     initial actorRef of an instance of the SingleAxisPublisher as [[scala.Option]]
 */
class MotionTelemetryGenerationHandler(assemblyConfig: MultiAxisAssemblyConfig) extends TelemetryGeneratorHandler {

  import org.tmt.aps.ics.hcd.GalilHCD._

  def generateAxisStateUpdate(cs: CurrentState): AxisStateUpdate = {

    // TODO: axisName needs to be part of CurrentState

    val axisNameKey = StringKey("axisName");

    val axisName = cs(axisNameKey).values.head

    assemblyConfig.axesmap foreach { case (key, value) => println(key + "-->" + value) }

    val axisConfig = assemblyConfig.axesmap.get(axisName)

    // convert position from postionKey from encoder to meters
    val posItem = cs(positionKey);
    val posEnc = posItem.values(0);
    // convert to meters
    val stagePosition: Double = encoderToStagePosition(axisConfig.get, posEnc)

    val stagePositionKey = DoubleKey("stagePosition")
    val stagePositionUnits = millimeters

    val stagePositionItem = stagePositionKey -> stagePosition withUnits stagePositionUnits

    AxisStateUpdate(cs(axisNameKey), cs(positionKey), cs(stateKey), cs(inLowLimitKey), cs(inHighLimitKey), cs(inHomeKey), stagePositionItem)
  }

}

