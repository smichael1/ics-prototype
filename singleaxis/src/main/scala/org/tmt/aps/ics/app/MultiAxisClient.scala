package org.tmt.aps.ics.app

import org.tmt.aps.ics.shared.AssemblyClientHelper._
import org.tmt.aps.ics.assembly.motion.{MultiAxisMotionAssemblyApi}
import akka.util.Timeout
import csw.services.ccs.BlockingAssemblyClient
import csw.services.ccs.CommandStatus.CommandResult

import csw.services.events.{TelemetryService}

/**
 * Starts standalone client application.
 */
object MultiAxisClient extends App {

  import csw.services.sequencer.SequencerEnv._
  import csw.services.loc.LocationService;
  import scala.concurrent._
  import scala.concurrent.duration._

  // Setup component helper for commands, configs, etc and clients to send to

  LocationService.initInterface();

  val taName = "stimulusPupilStageAssembly"
  val thName = "icsGalilHCD"

  val componentPrefix: String = "org.tmt.aps.ics.stimulusPupilStageAssembly"

  val stimulusSourceApi = MultiAxisMotionAssemblyApi(componentPrefix)

  val assemblyClient: BlockingAssemblyClient = resolveAssembly(taName)

  val hcdClient: HcdClient = resolveHcd(thName)

  LocationService.initInterface()

  val telemetryService: TelemetryService = Await.result(TelemetryService(), timeout.duration)

  getStatusEvents(telemetryService, componentPrefix)

  val obsId: String = "testObsId"

  val commandResult1 = stimulusSourceApi.init(assemblyClient, obsId, "stimulusPupilX")

  //val commandResult2 = stimulusSourceApi.position(assemblyClient, obsId, "stimulusSourceX", "absolute", "stage", 2.0)

}
