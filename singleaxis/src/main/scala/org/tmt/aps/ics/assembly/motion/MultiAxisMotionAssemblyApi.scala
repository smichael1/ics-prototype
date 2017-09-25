package org.tmt.aps.ics.assembly.motion

import csw.util.config.Configurations.{ConfigKey, SetupConfig}
import csw.services.ccs.Validation._
import scala.util.Try
import org.tmt.aps.ics.assembly.SingleAxisConfig
import org.tmt.aps.ics.assembly.AssemblyApi
import org.tmt.aps.ics.assembly.Converter

case class MultiAxisMotionAssemblyApi(componentPrefix: String) extends MotionAssemblyApi(componentPrefix) {

  override def validateSignature(candidateSC: SetupConfig): Validation = {

    println(s"initCK = ${initCK}")

    candidateSC.configKey match {
      case `initCK`     => initValidation(candidateSC)
      case `datumCK`    => datumValidation(candidateSC)
      case `stopCK`     => stopValidation(candidateSC)
      case `positionCK` => positionValidation(candidateSC)
      case x            => Invalid(OtherIssue(s"SetupConfig with prefix $x is not supported"))
    }
  }

  /**
   * multi-axis specific command signature validation functions
   */

  def positionValidation(candidateSC: SetupConfig): Validation = {

    // check that all the parameters are present and correct
    val vl1 = validateScParam(candidateSC, positionParamKey, positionParamUnits)
    val vl2 = validateScParam(candidateSC, typeParamKey)
    val vl3 = validateScParam(candidateSC, coordinateParamKey)

    //val vl4 = validateScParam(candidateSC, axisNameParamKey)
    // TODO: need to validate axisName against the axis names in the multiAxisAssemblyConfig
    val vl4 = Valid

    val validationList: ValidationList = List(vl1, vl2, vl3, vl4)

    // Returns a list of all failed validations 
    val invalids: List[Invalid] = validationList.collect { case a: Invalid => a }

    if (invalids.isEmpty) {
      Valid
    } else {
      invalids.head
    }
  }

  override def validateParamValues(candidateSC: SetupConfig): Validation = {
    Valid
  }

  def commandEncoderValue(sc: SetupConfig, currentStagePos: Option[Double], referencePos: Option[Double])(implicit maapi: MotionAssemblyApi, sac: SingleAxisConfig): Int = {
    // if sc is coordinates user, convert to stage
    val distance = sc(maapi.positionParamKey).values.head
    val coord = sc(maapi.coordinateParamKey)
    val cmdType = sc(maapi.typeParamKey)

    // get the stage distance, depending on the coord param
    val stageDistance = coord.head.name match {
      case "stage" => distance
      case _ => {
        // must be a user coord.  Use the conversion
        Converter.userPositionToStagePosition(sac, distance)
      }
    }

    // depending on 'type' of position command, determine the stage position 

    val stagePosition = cmdType.head.name match {
      case "absolute" => stageDistance
      case "relative" => stageDistance + currentStagePos.getOrElse(0.0)
      case "offsetFromReference" => {
        val reference = referencePos.getOrElse(0.0)
        // convert to stage coordinates
        val referenceStage = Converter.userPositionToStagePosition(sac, reference)

        stageDistance + referenceStage
      }
    }

    // convert to encoder value
    Converter.stagePositionToEncoder(sac, stagePosition)

  }

  /**
   * Validation for the position SetupConfig -- must have a single parameter named rangeDistance
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  def oldPositionValidation(sc: SetupConfig)(implicit maapi: MotionAssemblyApi, sac: SingleAxisConfig, currentStagePosition: Option[Double]): Validation = {

    val encoderPos = commandEncoderValue(sc, currentStagePosition, Some(sac.userReferencePoint))

    // compare command position with max and min allowable values from the configuration
    if (encoderPos < sac.encoderMin) {
      Invalid(ItemValueOutOfRangeIssue(s"Position value of ${encoderPos} must be greater than ${sac.encoderMin}"))
    } else if (encoderPos > sac.encoderMax) {
      Invalid(ItemValueOutOfRangeIssue(s"Position value of ${encoderPos} must be less than ${sac.encoderMax}"))
    } else {
      Valid
    }

  }

}