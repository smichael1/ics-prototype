package org.tmt.aps.ics

import akka.actor.ActorSystem
import akka.util.Timeout
import com.typesafe.config.ConfigFactory
import org.tmt.aps.ics.assembly.SingleAxisAssembly
import org.tmt.aps.ics.hcd.GalilHCD
import csw.services.cs.akka.ConfigServiceClient
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Helper class for setting up the test environment
 */
object TestEnv {

  // For the tests, store the HCD's configuration in the config service (Normally, it would already be there)
  def createTromboneHcdConfig()(implicit system: ActorSystem): Unit = {
    val config = ConfigFactory.parseResources(GalilHCD.resource.getPath)
    implicit val timeout = Timeout(5.seconds)
    Await.ready(ConfigServiceClient.saveConfigToConfigService(GalilHCD.galilConfigFile, config), timeout.duration)
  }

  // For the tests, store the assembly's configuration in the config service (Normally, it would already be there)
  def createTromboneAssemblyConfig()(implicit system: ActorSystem): Unit = {
    createTromboneHcdConfig()
    implicit val timeout = Timeout(5.seconds)
    val config = ConfigFactory.parseResources(SingleAxisAssembly.resource.getPath)
    Await.ready(ConfigServiceClient.saveConfigToConfigService(SingleAxisAssembly.singleAxisConfigFile, config), 5.seconds)

  }
}
