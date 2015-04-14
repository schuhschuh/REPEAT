// =============================================================================
// Project: Registration Performance Assessment Tool (REPEAT)
// Module:  OpenMOLE script for initial spatial normalization to MNI space
//
// Copyright (c) 2015, Andreas Schuh.
// See LICENSE file for license information.
// =============================================================================

import java.io.File
import com.andreasschuh.repeat._

val configFile = GlobalSettings().configFile

// Environment on which to execute registrations
val parEnv = Environment.short
val symLnk = Environment.symLnk

// Constants
val refId  = Constants.refId
val imgCsv = Constants.imgCsv
val imgDir = Constants.imgIDir
val imgPre = Constants.imgPre
val imgSuf = Constants.imgSuf
val dofSuf = Constants.dofSuf
val dofDir = Constants.dofDir
val logDir = Constants.logDir
val logSuf = Constants.logSuf

// Variables
val refIm    = Val[File]
val srcId    = Val[Int]
val srcIm    = Val[File]
val dof6     = Val[File]
val dof6Log  = Val[File]
val dof12    = Val[File]
val dof12Log = Val[File]

// Exploration task which iterates the image IDs and file paths
val srcIdSampling = CSVSampling(imgCsv) set (columns += ("ID", srcId))
val forEachIm = ExplorationTask(
    srcIdSampling x
    (refIm in SelectFileDomain(Constants.refIm.getParentFile, Constants.refIm.getName)) x
    (srcIm in SelectFileDomain(imgDir, imgPre + "${srcId}" + imgSuf))
  ) set (name := "forEachIm")

// Rigid registration mole
val dof6Path    = Path.join(dofDir, "rigid", refId + ",${srcId}" + dofSuf)
val dof6LogPath = Path.join(logDir, "rigid", refId + ",${srcId}" + logSuf)

val rigidBegin = EmptyTask() set (
    name    := "rigidBegin",
    inputs  += (refIm, srcId, srcIm),
    outputs += (refIm, srcId, srcIm, dof6)
  ) source FileSource(dof6Path, dof6)

val _rigidReg = ScalaTask(
  s"""
    | GlobalSettings.setConfigDir(workDir)
    |
    | val tgt     = new java.io.File(workDir, "${Constants.refIm.getName}")
    | val src     = new java.io.File(workDir, "$imgPre" + srcId + "$imgSuf")
    | val dof6    = new java.io.File(workDir, "result$dofSuf")
    | val dof6Log = new java.io.File(workDir, "output$logSuf")
    |
    | IRTK.ireg(tgt, src, None, dof6, Some(dof6Log),
    |   "Transformation model" -> "Rigid",
    |   "Background value" -> 0
    | )
  """.stripMargin)

val rigidReg = (configFile match {
    case Some(file) => _rigidReg.addResource(file)
    case None => _rigidReg
  }) set (
    name        := "rigidReg",
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (GlobalSettings.getClass, IRTK.getClass),
    inputs      += srcId,
    inputFiles  += (refIm, Constants.refIm.getName, symLnk),
    inputFiles  += (srcIm, imgPre + "${srcId}" + imgSuf, symLnk),
    outputFiles += ("result" + dofSuf, dof6),
    outputFiles += ("output" + logSuf, dof6Log),
    outputs     += (refIm, srcId, srcIm)
  ) hook (
    CopyFileHook(dof6,    dof6Path),
    CopyFileHook(dof6Log, dof6LogPath)
  ) on parEnv

val rigidMole = rigidBegin -- Skip(rigidReg, "dof6.exists()")

// Affine registration mole
val dof12Path    = Path.join(dofDir, "affine", refId + ",${srcId}" + dofSuf)
val dof12LogPath = Path.join(logDir, "affine", refId + ",${srcId}" + logSuf)

val affineBegin = EmptyTask() set (
    name    := "affineBegin",
    inputs  += (refIm, srcId, srcIm, dof6),
    outputs += (refIm, srcId, srcIm, dof6, dof12)
  ) source FileSource(dof12Path, dof12)

val _affineReg = ScalaTask(
  s"""
    | GlobalSettings.setConfigDir(workDir)
    |
    | val tgt      = new java.io.File(workDir, "${Constants.refIm.getName}")
    | val src      = new java.io.File(workDir, "$imgPre" + srcId + "$imgSuf")
    | val ini      = new java.io.File(workDir, "$refId," + srcId + "$dofSuf")
    | val dof12    = new java.io.File(workDir, "result$dofSuf")
    | val dof12Log = new java.io.File(workDir, "output$logSuf")
    |
    | IRTK.ireg(tgt, src, Some(ini), dof12, Some(dof12Log),
    |   "Transformation model" -> "Affine",
    |   "Padding value" -> 0
    | )
  """.stripMargin)

val affineReg = (configFile match {
    case Some(file) => _affineReg.addResource(file)
    case None => _affineReg
  }) set (
    name        := "affineReg",
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (GlobalSettings.getClass, IRTK.getClass),
    inputs      += srcId,
    inputFiles  += (refIm, Constants.refIm.getName, symLnk),
    inputFiles  += (srcIm, imgPre + "${srcId}" + imgSuf, symLnk),
    inputFiles  += (dof6, refId + ",${srcId}" + dofSuf, symLnk),
    outputFiles += ("result" + dofSuf, dof12),
    outputFiles += ("output" + logSuf, dof12Log),
    outputs     += (refIm, srcId, srcIm)
  ) hook (
    CopyFileHook(dof12,    dof12Path),
    CopyFileHook(dof12Log, dof12LogPath)
  ) on parEnv

val affineMole = affineBegin -- Skip(affineReg, "dof12.lastModified() >= dof6.lastModified()")

// Run spatial normalization pipeline for each input image
val exec = forEachIm -< rigidMole -- affineMole start
exec.waitUntilEnded()
