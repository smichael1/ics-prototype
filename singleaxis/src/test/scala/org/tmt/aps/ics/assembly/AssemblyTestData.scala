package org.tmt.aps.ics.assembly

import org.tmt.aps.ics.assembly.SingleAxisAssemblyConfig.{SingleAxisCalculationConfig, SingleAxisControlConfig}
import csw.services.loc.Connection.AkkaConnection
import csw.services.loc.{ComponentId, ComponentType, Connection}
import csw.services.loc.ConnectionType.AkkaType
import csw.services.pkg.Component.{AssemblyInfo, DoNotRegister, RegisterAndTrackServices}

/**
 * TMT Source Code: 8/12/16.
 */
object AssemblyTestData {

  val hcdId = ComponentId("icsGalilHCD", ComponentType.HCD)

  val TestAssemblyInfo = AssemblyInfo(
    "icsSingleAxis",
    "org.tmt.aps.ics.singleAxis",
    "org.tmt.aps.ics.assembly.SingleAxisAssembly",
    RegisterAndTrackServices, Set(AkkaType), Set(AkkaConnection(hcdId))
  )

  val TestCalculationConfig = SingleAxisCalculationConfig(
    defaultInitialElevation = 95.0
  )

  val TestControlConfig = SingleAxisControlConfig(
    positionScale = 8.0,
    stageZero = 90.0,
    minStageEncoder = 225,
    minEncoderLimit = 200,
    maxEncoderLimit = 1200,
    minPosition = 0.8,
    maxPosition = 1.8
  )

  val TestAssemblyContext = AssemblyContext(TestAssemblyInfo)
  
  val TestAssemblyConfig = SingleAxisAssemblyConfig( TestCalculationConfig, TestControlConfig);

}
