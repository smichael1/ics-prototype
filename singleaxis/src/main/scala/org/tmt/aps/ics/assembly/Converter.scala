package org.tmt.aps.ics.assembly

import org.tmt.aps.ics.assembly.SingleAxisAssemblyConfig.SingleAxisControlConfig
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
  def stagePositionToEncoder(controlConfig: SingleAxisControlConfig, stagePosition: Double): Int = {
    (controlConfig.encoderToStageScale * (stagePosition - controlConfig.stageHome) + controlConfig.encoderHome).toInt
  }
  
  /**
   * Convert an encoder count to a stage position
   * @param position in units of encoder
   * @return stagePosition is the value of the stage position in millimeters
   */
  def encoderToStagePosition(controlConfig: SingleAxisControlConfig, encoderCounts: Int): Double = {
    ((encoderCounts - controlConfig.encoderHome) / controlConfig.encoderToStageScale) + controlConfig.stageHome

  }
  
  /**
   * Convert a user position to stage position
   * @param userPosition is the value of the user position in meters or radians
   * @return stage position in mm
   */
  def userPositionToStagePosition(controlConfig: SingleAxisControlConfig, userPosition: Double): Double = {
    (controlConfig.stageToUserScale * (userPosition - controlConfig.userHome) + controlConfig.stageHome)
  }
  
  /**
   * Convert a stage position to a user position
   * @param stage position in mm
   * @return userPosition is the value of the user position in meters or radians
   */
  def stagePositionToUserPosition(controlConfig: SingleAxisControlConfig, stagePosition: Double): Double = {
    ((stagePosition - controlConfig.stageHome) / controlConfig.stageToUserScale) + controlConfig.userHome
  }

}
