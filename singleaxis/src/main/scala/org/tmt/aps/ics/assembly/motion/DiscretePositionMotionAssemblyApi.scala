package org.tmt.aps.ics.assembly.motion

import csw.util.config.Configurations.{ConfigKey, SetupConfig}
import csw.services.ccs.Validation._
import scala.util.Try

case class DiscretePositionMotionAssemblyApi(componentPrefix: String) extends MotionAssemblyApi(componentPrefix) {
  
  override def validateSignature(candidateSC: SetupConfig): Validation = {
    
      candidateSC.configKey match {
      case initCK     => initValidation(candidateSC)
      case datumCK    => datumValidation(candidateSC)
      case stopCK     => stopValidation(candidateSC)
      case positionCK => positionValidation(candidateSC)
      case x          => Invalid(OtherIssue(s"SetupConfig with prefix $x is not supported"))
    }
  }
  
  /**
   * multi-axis specific command signature validation functions
   */
  
  def positionValidation(candidateSC: SetupConfig): Validation = {

    // check that all the parameters are present and correct
    val vl1 = validateScParam(candidateSC, posSelectParamKey)
    val vl2 = validateScParam(candidateSC, axisParamKey)
        
    val validationList: ValidationList = List(vl1, vl2)
    
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
  
  
}