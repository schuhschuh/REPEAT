package com.andreasschuh.repeat.app

import java.io.{ ByteArrayInputStream, File }
import scala.sys.process._


/**
 * Main REPEAT application executing the OpenMOLE workflows
 */
object Main extends App {

  /**
   * File path of the REPEAT OpenMOLE plugin .jar file itself
   */
  val repeatPluginJar = new File(Main.getClass.getProtectionDomain.getCodeSource.getLocation.toURI)

  /**
   * Test OpenMOLE script running the overlap evaluation workflow
   */
  val script =
    """
      | import com.andreasschuh.repeat.workflow._
      | val exec = EvaluateOverlap(args(0)) start
      | exec.waitUntilEnded
    """.stripMargin

  // Execute workflow script in OpenMOLE console
  val istream = new ByteArrayInputStream(script.getBytes("UTF-8"))
  val logger  = new Logger
  Seq("openmole", "-c", "-p", repeatPluginJar.getAbsolutePath, "--", args(0)) #< istream ! logger
}