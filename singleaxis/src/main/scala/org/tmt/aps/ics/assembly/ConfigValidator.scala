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
  def invalidsInSingleAxisSetupConfigArg(sca: SetupConfigArg)(implicit aapi: AssemblyApi, saac: SingleAxisAssemblyConfig): List[Invalid] =
    // Returns a list of all failed validations in config arg
    validateSetupConfigArg(sca).collect { case a: Invalid => a }

  /**
   * Runs Trombone-specific validation on a single SetupConfig.
   * @return
   */
  def validateOneSetupConfig(sc: SetupConfig)(implicit aapi: AssemblyApi, saac: SingleAxisAssemblyConfig): Validation = {
    
    val signatureValid = aapi.validateSignature(sc)
    
    val paramValueValid = aapi.validateParamValues(sc)
    
    val validationList: ValidationList = List(signatureValid, paramValueValid)
    
    // Returns a list of all failed validations 
    val invalids: List[Invalid] = validationList.collect { case a: Invalid => a }
    
    if (invalids.isEmpty) {
      Valid
    } else {
      invalids.head
    }
  }

  // Validates a SetupConfigArg for SingleAxis Assembly
  def validateSetupConfigArg(sca: SetupConfigArg)(implicit aapi: AssemblyApi, saac: SingleAxisAssemblyConfig): ValidationList =
    sca.configs.map(config => validateOneSetupConfig(config)).toList




}
