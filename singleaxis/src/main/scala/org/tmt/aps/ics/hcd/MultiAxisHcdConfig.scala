package org.tmt.aps.ics.hcd

import com.typesafe.config.Config
import scala.language.postfixOps
import scala.collection.JavaConverters._

case class MultiAxisHcdConfig(axesmap: Map[String, AxisConfig]) {

}
/**
 * Axis configuration
 */
object AxisConfig {
  def apply(config: Config): AxisConfig = {
    // Main prefix for keys used below

    println("We got into AxisConfig apply() method")

    val axisName: String = config.getString("axisName")
    val galilAxis: String = config.getString("galilAxis")
    val lowLimit: Int = config.getInt("lowLimit")
    val highLimit: Int = config.getInt("highLimit")
    val home: Int = config.getInt("home")
    val startPosition: Int = config.getInt("startPosition")
    val stepDelayMS: Int = config.getInt("stepDelayMS")

    AxisConfig(axisName, galilAxis, lowLimit, highLimit, home, startPosition, stepDelayMS)
  }
}

/**
 * Axis configuration
 */
case class AxisConfig(axisName: String, galilAxis: String, lowLimit: Int, highLimit: Int, home: Int, startPosition: Int, stepDelayMS: Int)

object MultiAxisHcdConfig {

  def apply(config: Config): MultiAxisHcdConfig = {

    println("We got into MultiAxisHcdConfig apply() method")

    val prefix = "org.tmt.aps.ics.galilHCD"

    // 2. decode the config for each axis

    val axesList = config.getConfigList(s"$prefix.axes").asScala

    val axesmap = axesList map (axisConfig => axisConfig.getString("axisName") -> AxisConfig(axisConfig)) toMap

    MultiAxisHcdConfig(axesmap)

  }

  def apply(): MultiAxisHcdConfig = {
    MultiAxisHcdConfig(Map[String, AxisConfig]())
  }
}