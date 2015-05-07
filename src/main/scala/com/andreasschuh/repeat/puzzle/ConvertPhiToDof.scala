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

package com.andreasschuh.repeat.puzzle

import java.io.File
import scala.language.reflectiveCalls

import org.openmole.core.dsl._
import org.openmole.core.workflow.data.Prototype
import org.openmole.plugin.hook.file.CopyFileHook
import org.openmole.plugin.task.scala._
import org.openmole.plugin.source.file._
import org.openmole.plugin.tool.pattern.Skip

import com.andreasschuh.repeat.core._


/**
 * Convert output transformation to IRTK format (optional)
 */
object ConvertPhiToDof {

  /**
   * @param reg    Registration info
   * @param parId  ID of parameter set
   * @param phiDof Output transformation ("<phi>") of registration.
   *               If no phi2dof conversion is provided, tools for applying the transformation and computing
   *               evaluation measures such as the Jacobian determinant must be provided instead.
   *               These are then used instead for the assessment of the registration quality.
   * @param outDof Output transformation in IRTK format.
   *
   * @return Puzzle piece for conversion from IRTK format to format required by registration
   */
  def apply(reg: Registration, parId: Prototype[Int],
            tgtId: Prototype[Int], srcId: Prototype[Int],
            phiDof: Prototype[File], outDof: Prototype[File]) = {

    import Workspace.{dofPre, dofSuf}
    import FileUtil.join

    val outDofPath = join(reg.dofDir, dofPre + "${tgtId},${srcId}" + dofSuf).getAbsolutePath

    val begin = Capsule(EmptyTask() set (
        name    := s"${reg.id}-ConvertPhiToDofBegin",
        outputs += outDof
      ), strainer = true) source FileSource(outDofPath, outDof)

    val phi2dof = reg.phi2dofCmd match {
      case Some(cmd) =>
        val command = Val[Cmd]
        val task = ScalaTask(
          s"""
            | val ${outDof.name} = new java.io.File(workDir, "phi$dofSuf")
            | val args = Map(
            |   "regId"  -> "${reg.id}",
            |   "parId"  -> ${parId.name}.toString,
            |   "in"     -> ${phiDof.name}.getPath,
            |   "phi"    -> ${phiDof.name}.getPath,
            |   "dof"    -> ${outDof.name}.getPath,
            |   "out"    -> ${outDof.name}.getPath,
            |   "dofout" -> ${outDof.name}.getPath
            | )
            | val cmd = Registration.command(command, args)
            | val str = cmd.mkString("\\nREPEAT> \\"", "\\" \\"", "\\"\\n")
            | print(str)
            | FileUtil.mkdirs(${phiDof.name})
            | val ret = cmd.!
            | if (ret != 0) throw new Exception("Failed to convert output transformation")
          """.stripMargin) set(
            name        := s"${reg.id}-ConvertPhiToDof",
            imports     += ("com.andreasschuh.repeat.core.{FileUtil, Registration}", "scala.sys.process._"),
            usedClasses += (FileUtil.getClass, Registration.getClass),
            inputs      += (parId, tgtId, srcId, command),
            inputFiles  += (phiDof, dofPre + "${tgtId},${srcId}" + reg.phiSuf, link = Workspace.shared),
            outputs     += outDof,
            command     := cmd
          )
        Capsule(task, strainer = true) hook CopyFileHook(outDof, outDofPath, move = Workspace.shared)
      case None =>
        val task = EmptyTask() set (name := s"${reg.id}-UsePhiAsDof")
        Capsule(task, strainer = true).toPuzzlePiece
    }

    begin -- Skip(phi2dof, s"${outDof.name}.lastModified() > ${phiDof.name}.lastModified()")
  }
}
