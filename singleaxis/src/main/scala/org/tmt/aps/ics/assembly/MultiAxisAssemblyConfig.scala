package org.tmt.aps.ics.assembly

import com.typesafe.config.Config
import scala.language.postfixOps
import scala.collection.JavaConverters._

/**
 *
 */
case class MultiAxisAssemblyConfig(assemblyType: String, axesmap: Map[String, SingleAxisConfig]) {

}

case class SingleAxisConfig(

  axisName:            String,
  axisType:            String,
  hcdName:             String,
  hcdChannelName:      String,
  encoderMax:          Int,
  encoderMin:          Int,
  encoderHome:         Int,
  stageHome:           Double,
  userHome:            Double,
  userReferencePoint:  Double,
  encoderToStageScale: Double,
  stageToUserScale:    Double
)

object SingleAxisConfig {

  def apply(config: Config): SingleAxisConfig = {

    println("We got into SingleAxisConfig apply() method")

    val axisName = config.getString("axisName")
    val axisType = config.getString("axisType")
    val hcdName = config.getString("hcdName")
    val hcdChannelName = config.getString("hcdChannelName")
    val encoderMax = config.getInt("encoderMax")
    val encoderMin = config.getInt("encoderMin")
    val encoderHome = config.getInt("encoderHome")
    val stageHome = config.getDouble("stageHome")
    val userHome = config.getDouble("userHome")
    val userReferencePoint = config.getDouble("userReferencePoint")
    val encoderToStageScale = config.getDouble("encoderToStageScale")
    val stageToUserScale = config.getDouble("stageToUserScale")
    SingleAxisConfig(axisName, axisType, hcdName, hcdChannelName, encoderMax, encoderMin, encoderHome, stageHome, userHome, userReferencePoint, encoderToStageScale, stageToUserScale)
  }
}

object MultiAxisAssemblyConfig {

  def apply(config: Config): MultiAxisAssemblyConfig = {

    println("We got into MultiAxisAssemblyConfig apply() method")

    val prefix = "org.tmt.aps.ics.motion.assembly"

    // 1. decode the config to get top level items

    val assemblyType = config.getString(s"$prefix.assemblyType")

    // 2. decode the config for each axis

    val axesList = config.getConfigList(s"$prefix.axes").asScala

    val axesmap = axesList map (axisConfig => axisConfig.getString("axisName") -> SingleAxisConfig(axisConfig)) toMap

    MultiAxisAssemblyConfig(assemblyType, axesmap)

    //MultiAxisAssemblyConfig()

  }

  def apply(): MultiAxisAssemblyConfig = {
    MultiAxisAssemblyConfig("", Map[String, SingleAxisConfig]())
  }
}

