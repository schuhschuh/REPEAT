package com.andreasschuh.repeat

import java.io.File
import java.io.FileWriter
import scala.sys.process.ProcessLogger

/**
 * File process logger
 */
class TaskLogger(log: File) extends Configurable("workflow.log") with ProcessLogger {

  /// File writer object
  protected val writer = {
    val logFile = log.getAbsoluteFile()
    logFile.getParentFile().mkdirs()
    new FileWriter(logFile, getBooleanProperty("append"))
  }

  /// Whether to flush buffers after each line read from STDOUT (STDERR is always written immediately)
  protected val flush = getBooleanProperty("flush")

  /// Write line of process' STDOUT
  def out(s: => String): Unit = {
    writer.write(s)
    writer.write('\n')
    if (flush) writer.flush()
  }

  /// Write line of process' STDERR
  def err(s: => String): Unit = {
    writer.write(s)
    writer.write('\n')
    writer.flush()
  }

  /// Wrap process execution and close file when finished
  def buffer[T](f: => T): T = {
    val returnValue: T = f
    writer.close()
    returnValue
  }
}