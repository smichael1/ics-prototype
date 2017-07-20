package org.tmt.aps.ics.assembly

import org.tmt.aps.ics.assembly.AssemblyContext.{SingleAxisCalculationConfig, SingleAxisControlConfig}
import csw.util.config.DoubleItem

/**
 * This object contains functions that implement this test version of the single axis assembly algorithms.
 *
 */
object Converter {

  // sm - how do we make this generic for any axis?

  /**
   * Converts the component axis distance in user coordinates/units to stage position
   *
   * Configuration values determine what the range coordinates and stage coordinates will be and how to transform
   * TODO: implement.  For now just convert from meters to mm
   * TODO: do we have conversion constants?  Will we need them?  Do we want to be passing Double, Int, etc or a unit based object?
   *
   * @param distance (units to be determined by configuration)
   * @return stage position in millimeters
   */
  def distanceToStagePosition(distance: Double): Double = distance * 1000.0

  /**
   * Configuration values are passed in the controlConfig, which will be used to limit the values passed and define the position scale
   * @param stagePosition is the value of the stage position in millimeters
   * @return position in units of encoder
   */
  def stagePositionToEncoder(controlConfig: SingleAxisControlConfig, stagePosition: Double): Int = {
    // Scale value to be between 200 and 1000 encoder
    val encoderValue = (controlConfig.positionScale * (stagePosition - controlConfig.stageZero) + controlConfig.minStageEncoder).toInt
    val pinnedEncValue = Math.max(controlConfig.minEncoderLimit, Math.min(controlConfig.maxEncoderLimit, encoderValue))
    pinnedEncValue
  }

  /**
   * Configuration values are passed in the controlConfig, which will be used to limit the values passed and define the position scale
   * @param stagePosition is the value of the stage position in millimeters
   * @return position in units of encoder
   */
  def encoderToStagePosition(controlConfig: SingleAxisControlConfig, encoderCounts: Int): Double = {
    // Scale value to be between 200 and 1000 encoder
    ((encoderCounts - controlConfig.minStageEncoder) / controlConfig.positionScale) + controlConfig.stageZero

  }
}
