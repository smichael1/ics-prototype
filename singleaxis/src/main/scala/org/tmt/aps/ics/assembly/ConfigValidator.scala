package org.tmt.aps.ics.assembly

import csw.services.ccs.Validation._
import csw.util.config.Configurations.{SetupConfig, SetupConfigArg}
import com.typesafe.scalalogging.LazyLogging

import scala.util.Try

// TODO: work on how to get the componentHelper to be the implicit, not the AssemblyContext

/**
 * TMT Source Code: 4/17/17.
 */
object ConfigValidator extends LazyLogging {

  /**
   * Looks for any SetupConfigs in a SetupConfigArg that fail validation and returns as a list of only Invalid
   * @param sca input SetupConfigArg for checking
   * @param ac AssemblyContext provides command names
   * @return scala [[List]] that includes only the Invalid configurations in the SetupConfigArg
   */
  def invalidsInSingleAxisSetupConfigArg(sca: SetupConfigArg)(implicit ac: AssemblyContext): List[Invalid] =
    // Returns a list of all failed validations in config arg
    validateSingleAxisSetupConfigArg(sca).collect { case a: Invalid => a }

  /**
   * Runs Trombone-specific validation on a single SetupConfig.
   * @return
   */
  def validateOneSetupConfig(sc: SetupConfig)(implicit ac: AssemblyContext): Validation = {
    sc.configKey match {
      case ac.compHelper.initCK     => initValidation(sc)
      case ac.compHelper.datumCK    => datumValidation(sc)
      case ac.compHelper.stopCK     => stopValidation(sc)
      case ac.compHelper.positionCK => positionValidation(sc)
      case x                        => Invalid(OtherIssue(s"SetupConfig with prefix $x is not supported"))
    }
  }

  // Validates a SetupConfigArg for SingleAxis Assembly
  def validateSingleAxisSetupConfigArg(sca: SetupConfigArg)(implicit ac: AssemblyContext): ValidationList =
    sca.configs.map(config => validateOneSetupConfig(config)).toList

  /**
   * Validation for the init SetupConfig
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  def initValidation(sc: SetupConfig)(implicit ac: AssemblyContext): Validation = {

    val size = sc.size
    val ch = ac.compHelper
    if (sc.configKey != ac.compHelper.initCK) Invalid(WrongConfigKeyIssue("The SetupConfig is not an init configuration"))
    else // If no arguments, then this is okay
    if (sc.size == 0)
      Valid
    else if (size == 2) {
      import ac._
      // Check for correct keys and types
      // This example assumes that we want only these two keys
      val missing = sc.missingKeys(ch.configurationNameKey, ch.configurationVersionKey)
      if (missing.nonEmpty)
        Invalid(MissingKeyIssue(s"The 2 parameter init SetupConfig requires keys: $compHelper.configurationNameKey and $compHelper.configurationVersionKey"))
      else if (Try(sc(compHelper.configurationNameKey)).isFailure || Try(sc(compHelper.configurationVersionKey)).isFailure)
        Invalid(WrongItemTypeIssue(s"The init SetupConfig requires StringItems named: $compHelper.configurationVersionKey and $compHelper.configurationVersionKey"))
      else Valid
    } else Invalid(WrongNumberOfItemsIssue(s"The init configuration requires 0 or 2 items, but $size were received"))
  }

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

  /**
   * Validation for the position SetupConfig -- must have a single parameter named rangeDistance
   * @param sc the received SetupConfig
   * @return Valid or Invalid
   */
  def positionValidation(sc: SetupConfig)(implicit ac: AssemblyContext): Validation = {

    // The spec says parameter is not required, but doesn't explain so requiring parameter
    // Check for correct key and type -- only checks that essential key is present, not strict

    val helper = ac.compHelper;

    if (!sc.exists(ac.compHelper.stimulusPupilXKey)) {
      Invalid(MissingKeyIssue(s"The position SetupConfig must have a DoubleItem named: ${ac.compHelper.stimulusPupilXKey}"))
    } else if (Try(sc(ac.compHelper.stimulusPupilXKey)).isFailure) {
      Invalid(WrongItemTypeIssue(s"The position SetupConfig must have a DoubleItem named: ${ac.compHelper.stimulusPupilXKey}"))
    } else if (sc(ac.compHelper.stimulusPupilXKey).units != ac.compHelper.stimulusPupilXUnits) {
      Invalid(WrongUnitsIssue(s"The position SetupConfig parameter: ${ac.compHelper.stimulusPupilXKey} must have units of: ${ac.compHelper.stimulusPupilXUnits}"))
    } else {

      val pos = sc(ac.compHelper.stimulusPupilXKey).head

      logger.info("min: " + ac.controlConfig.minPosition)
      logger.info("max: " + ac.controlConfig.maxPosition)

      // compare command position with max and min allowable values from the configuration
      if (pos < ac.controlConfig.minPosition) {
        Invalid(ItemValueOutOfRangeIssue(s"Position value of $pos must be greater than ${ac.controlConfig.minPosition}"))
      } else if (pos > ac.controlConfig.maxPosition) {
        Invalid(ItemValueOutOfRangeIssue(s"Position value of $pos must be less than ${ac.controlConfig.maxPosition}"))
      } else {
        Valid
      }

    }

  }

}
