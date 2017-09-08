package org.tmt.aps.ics.assembly

import com.typesafe.config.Config
import csw.services.loc.ComponentId
import csw.services.pkg.Component.AssemblyInfo
import csw.util.config.Configurations.{ConfigKey, SetupConfig}
import csw.util.config.UnitsOfMeasure.{degrees, kilometers, micrometers, millimeters}
import csw.util.config.{BooleanKey, DoubleItem, DoubleKey, StringKey}

import org.tmt.aps.ics.assembly.motion.MotionAssemblyApi

/**
 * TMT Source Code: 10/4/16.
 */
case class AssemblyContext(info: AssemblyInfo) {
  // Assembly Info
  // These first three are set from the config file
  val componentName: String = info.componentName
  val componentClassName: String = info.componentClassName
  val componentPrefix: String = info.prefix
  val componentType = info.componentType
  val fullName = s"$componentPrefix.$componentName"

  val assemblyComponentId = ComponentId(componentName, componentType)
  val hcdComponentId = info.connections.head.componentId // There is only one

  
  val compHelper = new MotionAssemblyApi(componentPrefix)
}

