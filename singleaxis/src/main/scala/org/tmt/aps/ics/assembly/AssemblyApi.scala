package org.tmt.aps.ics.assembly


import csw.util.config.Configurations.{ConfigKey, SetupConfig}
import csw.util.config.{BooleanKey, Configurations, DoubleItem, DoubleKey, IntItem, IntKey, DoubleArrayItem, DoubleArrayKey, DoubleArray, StringKey, ChoiceKey, Choices}
import csw.services.ccs.Validation._
import csw.util.config.UnitsOfMeasure.Units
import scala.util.Try

/**
 * TMT Source Code: 3/29/17.
 */
class AssemblyApi(componentPrefix: String) {

  
  def validateSignature(candidateSC: SetupConfig): Validation = {
    Valid
  }
  
  def validateParamValues(candidateSC: SetupConfig): Validation = {
    Valid
  }
  
  
  // functions that validate a setupConfig for a single parameter 
  
  // double with units
  def validateScParam(candidateSC: SetupConfig, paramKey: DoubleKey, units: Units): Validation = {
   
    if (!candidateSC.exists(paramKey)) {
      Invalid(MissingKeyIssue(s"The ${candidateSC.configKey} SetupConfig must have a DoubleItem named: ${paramKey}"))
    } else if (Try(candidateSC(paramKey)).isFailure) {
      Invalid(WrongItemTypeIssue(s"The ${candidateSC.configKey} SetupConfig must have a DoubleItem named: ${paramKey}"))
    } else if (candidateSC(paramKey).units != units) {
      Invalid(WrongUnitsIssue(s"The ${candidateSC.configKey} SetupConfig parameter: ${paramKey} must have units of: ${units}"))
    } else {
      Valid
    }
  }

  // choice item
  def validateScParam(candidateSC: SetupConfig, paramKey: ChoiceKey): Validation = {
   
    if (!candidateSC.exists(paramKey)) {
      Invalid(MissingKeyIssue(s"The ${candidateSC.configKey} SetupConfig must have a ChoiceItem named: ${paramKey}"))
    } else if (Try(candidateSC(paramKey)).isFailure) {
      Invalid(WrongItemTypeIssue(s"The ${candidateSC.configKey} SetupConfig must have a ChoiceItem named: ${paramKey}"))
    } else {
      Valid
    }
  }
  

}

