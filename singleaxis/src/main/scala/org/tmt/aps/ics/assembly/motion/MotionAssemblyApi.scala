package org.tmt.aps.ics.assembly.motion

import com.typesafe.config.Config
import csw.services.loc.ComponentId
import csw.services.pkg.Component.AssemblyInfo
import csw.util.config.Configurations.{ConfigKey, SetupConfig}
import csw.util.config.UnitsOfMeasure.{degrees, kilometers, micrometers, millimeters, meters}
import csw.services.ccs.BlockingAssemblyClient
import csw.util.config.{BooleanKey, Configurations, DoubleItem, DoubleKey, IntItem, IntKey, DoubleArrayItem, DoubleArrayKey, DoubleArray, StringKey, ChoiceKey, Choices}
import csw.services.ccs.CommandStatus.CommandResult
import csw.services.ccs.Validation._
import org.tmt.aps.ics.assembly.AssemblyApi

class MotionAssemblyApi(componentPrefix: String) extends AssemblyApi(componentPrefix) {

  /************************************************************************************************/
  // command ConfigKeys - all the command config keys for a motion assembly
  /************************************************************************************************/

  val initPrefix = s"$componentPrefix.init"
  val initCK: ConfigKey = initPrefix

  // Position submit command
  val positionPrefix = s"$componentPrefix.position"
  val positionCK: ConfigKey = positionPrefix

  // Position selection submit command
  val positionSelectPrefix = s"$componentPrefix.positionSelect"
  val positionSelectCK: ConfigKey = positionSelectPrefix

  // Datum submit command
  val datumPrefix = s"$componentPrefix.datum"
  val datumCK: ConfigKey = datumPrefix

  // Stop submit command
  val stopPrefix = s"$componentPrefix.stop"
  val stopCK: ConfigKey = stopPrefix

  /************************************************************************************************/
  // command parameter keys
  /************************************************************************************************/

  val positionParamKey = DoubleKey("position")
  val positionParamUnits = meters

  val typeChoices = Choices.from("absolute", "relative", "offsetFromReference")
  val typeParamKey = ChoiceKey("type", typeChoices)

  val coordinateChoices = Choices.from("m1", "sky", "stage")
  val coordinateParamKey = ChoiceKey("coordinate", coordinateChoices)

  val posSelectChoices = Choices.from("", "")
  val posSelectParamKey = ChoiceKey("positionSelection", posSelectChoices)

  val axisNameParamKey = StringKey("axisName")

  /************************************************************************************************/
  // setupConfigs - a function to create each setupConfig that can be used with a motion assembly
  /************************************************************************************************/

  // position setup config
  def positionSC(positionValue: Double, axisName: String, positionType: String, coordName: String): SetupConfig = {

    SetupConfig(positionCK).
      add(axisNameParamKey -> axisName).
      add(positionParamKey -> positionValue withUnits positionParamUnits).
      add(typeParamKey -> positionType).
      add(coordinateParamKey -> coordName)
  }

  // position selection setup config
  def positionSC(axisName: String, positionSelection: String): SetupConfig = {

    SetupConfig(positionCK).
      add(axisNameParamKey -> axisName).
      add(posSelectParamKey -> positionSelection)
  }

  // init setup config
  def initSC(axisName: String): SetupConfig = {
    SetupConfig(initCK).add(axisNameParamKey -> axisName)
  }

  // datum setup config
  def datumSC(axisName: String): SetupConfig = {
    SetupConfig(datumCK).add(axisNameParamKey -> axisName)
  }

  //******************************************************************//
  // signature validation functions common to all motion assemblies   //
  //******************************************************************//

  /**
   * Validation for init SetupConfig -- currently nothing to validate
   */
  def initValidation(sc: SetupConfig): Validation = Valid

  /**
   * Validation for the datum SetupConfig -- currently nothing to validate
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  def datumValidation(sc: SetupConfig): Validation = Valid

  /**
   * Validation for the stop SetupConfig -- currently nothing to validate
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  def stopValidation(sc: SetupConfig): Validation = Valid

  /************************************************************************************************/
  // helper command functions - these are used to send commands easily without dealing with the 
  // config keys or setupConfig plumbing
  /************************************************************************************************/

  /**
   * Send one position command to the assembly
   * @param tla the BlockingAssemblyClient returned
   *
   * @return CommandResult and the conclusion of execution
   */
  def position(tla: BlockingAssemblyClient, obsId: String, axisName: String, positionType: String, coordName: String, pos: Double): CommandResult = {
    tla.submit(Configurations.createSetupConfigArg(obsId, positionSC(pos, axisName, positionType, coordName)))
  }

  /**
   * Send one position command to the assembly
   * @param tla the BlockingAssemblyClient returned
   *
   * @return CommandResult and the conclusion of execution
   */
  def position(tla: BlockingAssemblyClient, obsId: String, axisName: String, positionSelection: String): CommandResult = {
    tla.submit(Configurations.createSetupConfigArg(obsId, positionSC(axisName, positionSelection)))
  }

  /**
   * Initializes the SingleAxis Assembly
   * @param tla the BlockingAssemblyClient returned by getSingleAxis
   * @return CommandResult and the conclusion of execution
   */
  def init(tla: BlockingAssemblyClient, obsId: String, axisName: String): CommandResult = {
    tla.submit(Configurations.createSetupConfigArg(obsId, initSC(axisName), datumSC(axisName)))
  }

  // A list of all commands, just do position for now
  val allCommandKeys: List[ConfigKey] = List(positionCK)

  val stagePositionKey = DoubleKey("stagePosition")
  val stagePositionUnits = millimeters
  def spos(pos: Double): DoubleItem = stagePositionKey -> pos withUnits stagePositionUnits

  // ----------- Keys, etc. used by Publisher, calculator, comamnds
  val engStatusEventPrefix = s"$componentPrefix.engr"
  val singleAxisStateStatusEventPrefix = s"$componentPrefix.state"
  val axisStateEventPrefix = s"$componentPrefix.axis1State"
  val axisStatsEventPrefix = s"$componentPrefix.axis1Stats"

}