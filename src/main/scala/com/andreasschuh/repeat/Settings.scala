package com.andreasschuh.repeat

import com.typesafe.config.ConfigFactory
import java.io.File
import java.net.URL

/**
 * Access values in configuration file
 *
 * This object reads the configuration from the following HOCON files:
 * 1. /repeat.conf
 * 2. $HOME/.openmole/repeat.conf
 * 3. $OPENMOLE/configuration/repeat.conf
 * 4. $JAR/reference.conf
 * where:
 * - $PWD is the current working directory
 * - $HOME is the home directory of the user
 * - $OPENMOLE is the location of the OpenMOLE installation
 * - $JAR is the root of the OpenMOLE plugin .jar file
 */
class Settings(configName: Option[String] = None, configDir: File = new File(System.getProperty("user.dir"))) {

  /// Found (main) configuration file
  val configFile: Option[File] = configName match {
    case Some(name) =>
      val file = new File(configDir, name)
      if (!file.exists()) throw new Exception("Configuration file does not exist: " + file.getAbsolutePath)
      Some(file)
    case None =>
      val localConfig = new File(configDir, "repeat.conf")
      if (localConfig.exists()) Some(localConfig.getAbsoluteFile)
      else {
        val homeConfig = new File(Path.join(System.getProperty("user.home"), ".openmole", "repeat.conf"))
        if (homeConfig.exists()) Some(homeConfig.getAbsoluteFile)
        else None
      }
  }

  /// Parsed configuration object
  private val config = {
    ConfigFactory.defaultOverrides().withFallback(configFile match {
      case Some(f) => ConfigFactory.parseFile(f)
      case None => ConfigFactory.empty()
    }).withFallback(System.getProperty("eclipse.application", "NotOpenMOLE") match {
      case "org.openmole.ui" => ConfigFactory.parseURL(new URL("platform:/plugin/com.andreasschuh.repeat/reference.conf"))
      case _ => ConfigFactory.defaultReference()
    }).resolve()
  }

  /// Get absolute path
  def getFile(propName: String): File = new File(config.getString(propName)).getAbsoluteFile

  /// Get absolute path string
  def getPath(propName: String): String = new File(config.getString(propName)).getAbsolutePath

  /// Get string value
  def getString(propName: String): String = config.getString(propName)

  /// Get boolean value
  def getBoolean(propName: String): Boolean = config.getBoolean(propName)

  /// Get integer value
  def getInt(propName: String): Int = config.getInt(propName)
}