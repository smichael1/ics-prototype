package org.tmt.aps.ics.assembly

import csw.util.config.DoubleItem

/**
 * This object contains functions to convert from/to encoder/stage stage/user coordinates.
 *
 */
object Converter {

  /**
   * Convert a stage position to encoder counts
   * @param stagePosition is the value of the stage position in millimeters
   * @return position in units of encoder
   */
  def stagePositionToEncoder(axisConfig: SingleAxisConfig, stagePosition: Double): Int = {
    (axisConfig.encoderToStageScale * (stagePosition - axisConfig.stageHome) + axisConfig.encoderHome).toInt
  }

  /**
   * Convert an encoder count to a stage position
   * @param position in units of encoder
   * @return stagePosition is the value of the stage position in millimeters
   */
  def encoderToStagePosition(axisConfig: SingleAxisConfig, encoderCounts: Int): Double = {
    ((encoderCounts - axisConfig.encoderHome) / axisConfig.encoderToStageScale) + axisConfig.stageHome

  }

  /**
   * Convert a user position to stage position
   * @param userPosition is the value of the user position in meters or radians
   * @return stage position in mm
   */
  def userPositionToStagePosition(axisConfig: SingleAxisConfig, userPosition: Double): Double = {
    (axisConfig.stageToUserScale * (userPosition - axisConfig.userHome) + axisConfig.stageHome)
  }

  /**
   * Convert a stage position to a user position
   * @param stage position in mm
   * @return userPosition is the value of the user position in meters or radians
   */
  def stagePositionToUserPosition(axisConfig: SingleAxisConfig, stagePosition: Double): Double = {
    ((stagePosition - axisConfig.stageHome) / axisConfig.stageToUserScale) + axisConfig.userHome
  }

}
