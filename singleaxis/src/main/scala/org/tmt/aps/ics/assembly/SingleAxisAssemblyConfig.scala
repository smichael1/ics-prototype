package org.tmt.aps.ics.assembly

import com.typesafe.config.Config
import org.tmt.aps.ics.assembly.SingleAxisAssemblyConfig.{SingleAxisControlConfig}
import csw.services.loc.ComponentId
import csw.services.pkg.Component.AssemblyInfo
import csw.util.config.Configurations.{ConfigKey, SetupConfig}
import csw.util.config.UnitsOfMeasure.{degrees, kilometers, micrometers, millimeters}
import csw.util.config.{BooleanKey, DoubleItem, DoubleKey, StringKey}

/**
 * TMT Source Code: 10/4/16.
 */
case class SingleAxisAssemblyConfig(controlConfig: SingleAxisControlConfig) {

}

object SingleAxisAssemblyConfig {

  /**
   * 
   */
  case class SingleAxisControlConfig(
  
   	axisName: String,
	  axisType: String,
	  hcdName: String,
	  hcdChannelName: String,
	  encoderMax: Int,
	  encoderMin: Int,
	  encoderHome: Int,
	  stageHome: Double, 
	  userHome: Double,
	  userReferencePoint: Double,
	  encoderToStageScale: Double,	
	  stageToUserScale: Double
  )
  
  

  object SingleAxisControlConfig {
    def apply(config: Config): SingleAxisControlConfig = {
      // Main prefix for keys used below
      val prefix = "org.tmt.aps.ics.singleAxis.assembly"

      val axisName = config.getString(s"$prefix.control-config.axisName")
      val axisType = config.getString(s"$prefix.control-config.axisType")
      val hcdName = config.getString(s"$prefix.control-config.hcdName")
      val hcdChannelName = config.getString(s"$prefix.control-config.hcdChannelName")
      val encoderMax = config.getInt(s"$prefix.control-config.encoderMax")
      val encoderMin = config.getInt(s"$prefix.control-config.encoderMin")
      val encoderHome = config.getInt(s"$prefix.control-config.encoderHome")
      val stageHome = config.getDouble(s"$prefix.control-config.stageHome")
      val userHome = config.getDouble(s"$prefix.control-config.userHome")
      val userReferencePoint = config.getDouble(s"$prefix.control-config.userReferencePoint")
      val encoderToStageScale = config.getDouble(s"$prefix.control-config.encoderToStageScale")
      val stageToUserScale = config.getDouble(s"$prefix.control-config.stageToUserScale")
      SingleAxisControlConfig(axisName, axisType, hcdName, hcdChannelName, encoderMax, encoderMin, encoderHome, stageHome, userHome, userReferencePoint, encoderToStageScale,	 stageToUserScale)
    }
  }



}
