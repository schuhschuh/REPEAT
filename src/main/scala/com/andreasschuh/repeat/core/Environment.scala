/*
 * Registration Performance Assessment Tool (REPEAT)
 *
 * Copyright (C) 2015  Andreas Schuh
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU Affero General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Contact: Andreas Schuh <andreas.schuh.84@gmail.com>
 */

package com.andreasschuh.repeat.core

import java.io.File

import org.openmole.core.exception.UserBadDataError
import org.openmole.core.workspace.{ Workspace => OpenMOLEWorkspace }
import org.openmole.core.workflow.execution.local.LocalEnvironment
import org.openmole.plugin.environment.ssh.{ PrivateKey, LoginPassword, SSHAuthentication }
import org.openmole.plugin.environment.slurm.SLURMEnvironment
import org.openmole.plugin.environment.condor.CondorEnvironment
import fr.iscpif.gridscale.condor.CondorRequirement


/**
 * Settings common to all OpenMOLE workflows
 */
object Environment extends Configurable("environment") {

  /** Add authentication method for named execution environment */
  private def addAuthenticationFor(name: String): Unit = {
    val login = getStringProperty(s"$name.user")
    val host  = getStringProperty(s"$name.host")
    try SSHAuthentication(login, host, 22)(OpenMOLEWorkspace.instance.authenticationProvider)
    catch {
      case _: UserBadDataError =>
        val auth = getStringProperty(s"$name.auth")
        auth match {
        case "id_dsa" | "id_rsa" =>
          val key = new File(new File(System.getProperty("user.home"), ".ssh"), auth)
          if (!key.isFile) throw new Exception("Private SSH key file " + key + " does not exist")
          SSHAuthentication += PrivateKey(key, login, "", host) // no passphrase allowed
        case password: String =>
          SSHAuthentication += LoginPassword(login, password, host)
      }
    }
  }

  /** Get named execution environment with specified properties */
  private def getEnvironment(name: String, memory: Option[Int] = None, nodes: Option[Int] = None, threads: Option[Int] = None) = {
    val parts    = name.split("-")
    val _name    = parts.head.toLowerCase
    val _queue   = if (parts.length > 1) parts.tail.mkString("-") else "long"
    val _memory  = Some(memory  getOrElse getIntProperty(s"${_name}.memory"))
    val _nodes   = Some(nodes   getOrElse getIntProperty(s"${_name}.nodes"))
    val _threads = Some(threads getOrElse getIntProperty(s"${_name}.threads"))
    val _requirements = getStringListProperty(s"${_name}.requirements").toList
    _name.toLowerCase match {
      case "slurm" =>
        addAuthenticationFor("slurm")
        SLURMEnvironment(
          getStringProperty("slurm.user"),
          getStringProperty("slurm.host"),
          getIntProperty("slurm.port"),
          queue = Some(getStringProperty(s"slurm.queue.${_queue}")),
          memory = _memory,
          nodes = _nodes,
          threads = _threads,
          constraints = _requirements,
          openMOLEMemory = Some(256),
          workDirectory = Workspace.openMOLE,
          storageSharedLocally = Workspace.shared
        )(OpenMOLEWorkspace.instance.authenticationProvider)
      case "condor" | "htcondor" =>
        addAuthenticationFor("condor")
        CondorEnvironment(
          getStringProperty("condor.user"),
          getStringProperty("condor.host"),
          getIntProperty("condor.port"),
          memory = _memory,
          nodes = nodes,
          threads = threads,
          requirements = _requirements.grouped(2).map(kv => CondorRequirement(kv.head, kv(1))).toList,
          openMOLEMemory = Some(256),
          workDirectory = Workspace.openMOLE,
          storageSharedLocally = Workspace.shared
        )(OpenMOLEWorkspace.instance.authenticationProvider)
      case "local" =>
        LocalEnvironment(_nodes match {
          case Some(n) if n > 0 => n
          case _ => Runtime.getRuntime.availableProcessors()
        })
      case _ => throw new Exception("Invalid execution environment: " + name)
    }
  }

  /** Environment for parallel tasks executed on the local machine */
  val local = getEnvironment("local")

  /** Environment on which to execute short running tasks */
  val short = getEnvironment(getStringProperty("short"))

  /** Environment on which to execute long running tasks */
  val long = getEnvironment(getStringProperty("long"))

  /** Get named execution environment with specified properties */
  def apply(name: Option[String] = None, memory: Option[Int] = None, nodes: Option[Int] = None, threads: Option[Int] = None) =
    getEnvironment(name getOrElse getStringProperty("long"), memory = memory, nodes = nodes, threads = threads)
}
