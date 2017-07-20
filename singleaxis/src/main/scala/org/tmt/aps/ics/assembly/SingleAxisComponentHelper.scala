package org.tmt.aps.ics.assembly

import com.typesafe.config.Config
//import org.tmt.aps.ics.assembly.AssemblyContext.{SingleAxisCalculationConfig, SingleAxisControlConfig}
import csw.services.loc.ComponentId
import csw.services.pkg.Component.AssemblyInfo
import csw.util.config.Configurations.{ConfigKey, SetupConfig}
import csw.util.config.UnitsOfMeasure.{degrees, kilometers, micrometers, millimeters, meters}
import csw.services.ccs.BlockingAssemblyClient
import csw.util.config.{BooleanKey, Configurations, DoubleItem, DoubleKey, IntItem, IntKey, DoubleArrayItem, DoubleArrayKey, DoubleArray, StringKey}
import csw.services.ccs.CommandStatus.CommandResult

/**
 * TMT Source Code: 3/29/17.
 */
case class SingleAxisComponentHelper(componentPrefix: String) {

  // Public command configurations - this is where we define configurations to be used by component tests, validation and client usage
  // Init submit command
  val initPrefix = s"$componentPrefix.init"
  val initCK: ConfigKey = initPrefix

  // Position submit command
  val positionPrefix = s"$componentPrefix.position"
  val positionCK: ConfigKey = positionPrefix

  // Datum submit command
  val datumPrefix = s"$componentPrefix.datum"
  val datumCK: ConfigKey = datumPrefix

  // Stop submit command
  val stopPrefix = s"$componentPrefix.stop"
  val stopCK: ConfigKey = stopPrefix

  // SingleAxisAssembly position setup config
  def positionSC(stimulusPupilX: Double): SetupConfig = SetupConfig(positionCK).add(stimulusPupilXKey -> stimulusPupilX withUnits stimulusPupilXUnits)

  val configurationNameKey = StringKey("initConfigurationName")
  val configurationVersionKey = StringKey("initConfigurationVersion")

  /**
   * Send one position command to the SingleAxis Assembly
   * @param tla the BlockingAssemblyClient returned by getSingleAxis
   * @param pos some position as a double.  Should be around 90-200 or you will drive it to a limit
   * @return CommandResult and the conclusion of execution
   */
  def position(tla: BlockingAssemblyClient, pos: Double, obsId: String): CommandResult = {
    tla.submit(Configurations.createSetupConfigArg(obsId, positionSC(pos)))
  }

  /**
   * Initializes the SingleAxis Assembly
   * @param tla the BlockingAssemblyClient returned by getSingleAxis
   * @return CommandResult and the conclusion of execution
   */
  def init(tla: BlockingAssemblyClient, obsId: String): CommandResult = {
    tla.submit(Configurations.createSetupConfigArg(obsId, SetupConfig(initCK), SetupConfig(datumCK)))
  }

  // A list of all commands, just do position for now
  val allCommandKeys: List[ConfigKey] = List(positionCK)

  // Shared key values --
  //val naRangeDistanceKey = DoubleKey("rangeDistance")
  //val naRangeDistanceUnits = kilometers

  val stimulusPupilXKey = DoubleKey("stimulusPupilX")
  val stimulusPupilXUnits = meters

  val stagePositionKey = DoubleKey("stagePosition")
  val stagePositionUnits = millimeters
  def spos(pos: Double): DoubleItem = stagePositionKey -> pos withUnits stagePositionUnits

  // ----------- Keys, etc. used by Publisher, calculator, comamnds
  val engStatusEventPrefix = s"$componentPrefix.engr"
  val singleAxisStateStatusEventPrefix = s"$componentPrefix.state"
  val axisStateEventPrefix = s"$componentPrefix.axis1State"
  val axisStatsEventPrefix = s"$componentPrefix.axis1Stats"

  /**
   * Test code for ICS API
   * These methods should fulfill the API defined in the prototype API document: TMT.CTR.ICD.17.006.DRF01
   *
   */

  // command configurations for ICS API

  // Reset submit command
  val resetPrefix = s"$componentPrefix.reset"
  val resetCK: ConfigKey = resetPrefix

  // offset submit command
  val offsetPrefix = s"$componentPrefix.offset"
  val offsetCK: ConfigKey = offsetPrefix

  // stagePosition
  val stagePositionPrefix = s"$componentPrefix.stagePosition"
  val stagePositionCK: ConfigKey = stagePositionPrefix

  // select
  val selectPrefix = s"$componentPrefix.select"
  val selectCK: ConfigKey = selectPrefix

  // stageSelections
  val stageSelectionsPrefix = s"$componentPrefix.stageSelections"
  val stageSelectionsCK: ConfigKey = stageSelectionsPrefix

  // stageReference
  val stageReferencePrefix = s"$componentPrefix.stageReference"
  val stageReferenceCK: ConfigKey = stageReferencePrefix

  val commandXKey = DoubleKey("commandX")
  val commandYKey = DoubleKey("commandY")
  val commandZKey = DoubleKey("commandZ")
  val commandPhiKey = DoubleKey("commandPhi")
  val selectKey = IntKey("select")
  val selectionsKey = DoubleArrayKey("selections")

  def positionStimulus(commandX: Boolean, deltaX: Double, commandY: Boolean, deltaY: Double, commandZ: Boolean, deltaZ: Double): SetupConfig = {

    val sc: SetupConfig = SetupConfig(positionCK)
    if (commandX) sc.add(commandXKey -> deltaX withUnits degrees)
    if (commandY) sc.add(commandYKey -> deltaY withUnits degrees)
    if (commandZ) sc.add(commandZKey -> deltaZ withUnits meters)
    sc
  }

  def offsetStimulus(commandX: Boolean, deltaX: Double, commandY: Boolean, deltaY: Double, commandZ: Boolean, deltaZ: Double): SetupConfig = {

    val sc: SetupConfig = SetupConfig(offsetCK)
    if (commandX) sc.add(commandXKey -> deltaX withUnits degrees)
    if (commandY) sc.add(commandYKey -> deltaY withUnits degrees)
    if (commandZ) sc.add(commandZKey -> deltaZ withUnits meters)
    sc
  }

  def positionPupil(commandX: Boolean, deltaX: Double, commandY: Boolean, deltaY: Double, commandPhi: Boolean, deltaPhi: Double): SetupConfig = {

    val sc: SetupConfig = SetupConfig(positionCK)
    if (commandX) sc.add(commandXKey -> deltaX withUnits meters)
    if (commandY) sc.add(commandYKey -> deltaY withUnits meters)
    if (commandPhi) sc.add(commandZKey -> deltaPhi withUnits degrees)
    sc
  }

  def offsetPupil(commandX: Boolean, deltaX: Double, commandY: Boolean, deltaY: Double, commandPhi: Boolean, deltaPhi: Double): SetupConfig = {

    val sc: SetupConfig = SetupConfig(offsetCK)
    if (commandX) sc.add(commandXKey -> deltaX withUnits meters)
    if (commandY) sc.add(commandYKey -> deltaY withUnits meters)
    if (commandPhi) sc.add(commandZKey -> deltaPhi withUnits degrees)
    sc
  }

  def positionStage(commandX: Boolean, deltaX: Double, commandY: Boolean, deltaY: Double, commandZ: Boolean, deltaZ: Double): SetupConfig = {

    val sc: SetupConfig = SetupConfig(stagePositionCK)
    if (commandX) sc.add(commandXKey -> deltaX withUnits millimeters)
    if (commandY) sc.add(commandYKey -> deltaY withUnits millimeters)
    if (commandZ) sc.add(commandZKey -> deltaZ withUnits millimeters)
    sc
  }

  def reset(): SetupConfig = {
    SetupConfig(resetCK)
  }

  def select(selection: Int): SetupConfig = {
    val sc: SetupConfig = SetupConfig(selectCK)
    sc.add(selectKey -> selection)
    sc
  }

  def setSelectionPoints(points: Array[Double]): SetupConfig = {
    val sc: SetupConfig = SetupConfig(stageSelectionsCK)
    sc.add(selectionsKey -> DoubleArray(points))
  }

  def setReferencePoints(commandX: Boolean, deltaX: Double, commandY: Boolean, deltaY: Double, commandZ: Boolean, deltaZ: Double): SetupConfig = {
    val sc: SetupConfig = SetupConfig(stageReferenceCK)
    if (commandX) sc.add(commandXKey -> deltaX withUnits millimeters)
    if (commandY) sc.add(commandYKey -> deltaY withUnits millimeters)
    if (commandZ) sc.add(commandZKey -> deltaZ withUnits millimeters)
    sc
  }

}

