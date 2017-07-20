package org.tmt.aps.ics.app

import org.tmt.aps.ics.shared.AssemblyClientHelper._
import org.tmt.aps.ics.assembly.SingleAxisComponentHelper
import akka.util.Timeout
import csw.services.ccs.BlockingAssemblyClient
import csw.services.ccs.CommandStatus.CommandResult

import csw.services.events.{TelemetryService}

/**
 * Starts standalone client application.
 */
object SingleAxisClient2 extends App {

  import csw.services.sequencer.SequencerEnv._
  import scala.concurrent._
  import scala.concurrent.duration._

  // Setup component helper for commands, configs, etc and clients to send to

  val taName = "singleAxis"
  val thName = "icsGalilHCD"

  val componentPrefix: String = "org.tmt.aps.ics.singleAxis"

  val compHelper = SingleAxisComponentHelper(componentPrefix)

  val assemblyClient: BlockingAssemblyClient = resolveAssembly(taName)

  val hcdClient: HcdClient = resolveHcd(thName)

  //LocationService.initInterface()

  val telemetryService: TelemetryService = Await.result(TelemetryService(), timeout.duration)

  getStatusEvents(telemetryService, componentPrefix)

  val obsId: String = "testObsId"

  //val commandResult1 = compHelper.init(assemblyClient, obsId)

  //val commandResult2 = compHelper.position(assemblyClient, 60.0, obsId)

}
