package org.tmt.aps.ics.app

import csw.services.apps.containerCmd.ContainerCmd

/**
 * Starts the HCD and/or assembly as a standalone application.
 */
object SingleAxisApp extends App {
  // This defines the names that can be used with the --start option and the config files used ("" is the default entry)
  val m = Map(
    "hcd" -> "galilHCD.conf",
    "assembly" -> "singleAxisAssembly.conf",
    "both" -> "singleAxisContainer.conf",
    "" -> "singleAxisContainer.conf" // default value
  )

  // Parse command line args for the application (app name is singleaxis, like the sbt project)
  ContainerCmd("singleaxis", args, m)
}
