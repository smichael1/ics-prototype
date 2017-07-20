package org.tmt.aps.ics.assembly

import com.typesafe.config.Config
import org.tmt.aps.ics.assembly.AssemblyContext.{SingleAxisCalculationConfig, SingleAxisControlConfig}
import csw.services.loc.ComponentId
import csw.services.pkg.Component.AssemblyInfo
import csw.util.config.Configurations.{ConfigKey, SetupConfig}
import csw.util.config.UnitsOfMeasure.{degrees, kilometers, micrometers, millimeters}
import csw.util.config.{BooleanKey, DoubleItem, DoubleKey, StringKey}

/**
 * TMT Source Code: 10/4/16.
 */
case class AssemblyContext(info: AssemblyInfo, calculationConfig: SingleAxisCalculationConfig, controlConfig: SingleAxisControlConfig) {
  // Assembly Info
  // These first three are set from the config file
  val componentName: String = info.componentName
  val componentClassName: String = info.componentClassName
  val componentPrefix: String = info.prefix
  val componentType = info.componentType
  val fullName = s"$componentPrefix.$componentName"

  val assemblyComponentId = ComponentId(componentName, componentType)
  val hcdComponentId = info.connections.head.componentId // There is only one

  val compHelper = SingleAxisComponentHelper(componentPrefix)

}

object AssemblyContext {

  /**
   * Configuration class
   *
   * @param positionScale   value used to scale
   * @param stageZero       zero point in stage conversion
   * @param minStageEncoder minimum
   * @param minEncoderLimit minimum
   */
  case class SingleAxisControlConfig(
    positionScale: Double,
    stageZero:     Double, minStageEncoder: Int,
    minEncoderLimit: Int, maxEncoderLimit: Int
  )

  object SingleAxisControlConfig {
    def apply(config: Config): SingleAxisControlConfig = {
      // Main prefix for keys used below
      val prefix = "org.tmt.aps.ics.singleAxis.assembly"

      val positionScale = config.getDouble(s"$prefix.control-config.positionScale")
      val stageZero = config.getDouble(s"$prefix.control-config.stageZero")
      val minStageEncoder = config.getInt(s"$prefix.control-config.minStageEncoder")
      val minEncoderLimit = config.getInt(s"$prefix.control-config.minEncoderLimit")
      val maxEncoderLimit = config.getInt(s"$prefix.control-config.maxEncoderLimit")
      SingleAxisControlConfig(positionScale, stageZero, minStageEncoder, minEncoderLimit, maxEncoderLimit)
    }
  }

  /**
   * Configuration class
   *
   * @param defaultInitialElevation a default initial eleveation (possibly remove once workign)
   * @param focusErrorGain          gain value for focus error
   * @param upperFocusLimit         check for maximum focus error
   * @param lowerFocusLimit         check for minimum focus error
   * @param zenithFactor            an algorithm value for scaling zenith angle term
   */
  case class SingleAxisCalculationConfig(defaultInitialElevation: Double)

  object SingleAxisCalculationConfig {
    def apply(config: Config): SingleAxisCalculationConfig = {
      // Main prefix for keys used below
      val prefix = "org.tmt.aps.ics.singleAxis.assembly"

      val defaultInitialElevation = config.getDouble(s"$prefix.calculation-config.defaultInitialElevation")

      SingleAxisCalculationConfig(defaultInitialElevation)
    }
  }

}
