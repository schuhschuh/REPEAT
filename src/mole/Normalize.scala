// OpenMOLE workflow for initial spatial normalization of images
import com.andreasschuh.repeat.Settings
import com.andreasschuh.repeat.IRTK

// Environment on which to execute registration tasks
val env = LocalEnvironment(1)

// Constants
val refId  = Settings.refId
val imgDir = Settings.imgIDir
val imgPre = Settings.imgPre
val imgSuf = Settings.imgSuf
val dofDir = Settings.dofDir
val dofSuf = Settings.dofSuf
val imgCsv = Settings.imgCsv

// Variables
val srcId = Val[Int]
val srcIm = Val[File]
val dof6  = Val[File]
val dof12 = Val[File]

// Exploration task which iterates the image IDs and file paths
val sampleId  = CSVSampling(imgCsv) set(columns += ("ID", srcId))
val srcImFile = FileSource(imgDir + "/" + imgPre + "${srcId}" + imgSuf, srcIm)
val forEachIm = ExplorationTask(sampleId) -< (EmptyTask() set(inputs += srcId, outputs += (srcId, srcIm)) source srcImFile)

// Rigid registration mole
val rigidOutputFile = FileSource(dofDir + "/rigid/"  + refId + ",${srcId}" + dofSuf, dof6)

val rigidBegin = EmptyTask() set(
    inputs  += (srcId, srcIm),
    outputs += (srcId, srcIm, dof6)
  ) source rigidOutputFile

val rigidReg = ScalaTask(
  """IRTK.ireg(Settings.refIm, srcIm, None, dof6,
    |  "No. of threads"       -> 8,
    |  "Transformation model" -> "Rigid",
    |  "Background value"     -> 0
    |)
  """.stripMargin) set(
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (Settings.getClass(), IRTK.getClass()),
    inputs      += (srcId, srcIm, dof6),
    outputs     += (srcId, srcIm, dof6)
  )

val rigidEnd = StrainerCapsule(EmptyTask())

val rigidMole = rigidBegin -- (((rigidReg on env) -- rigidEnd) when "!dof6.exists()", rigidEnd when "dof6.exists()")

// Affine registration mole
val affineOutputFile = FileSource(dofDir + "/affine/" + refId + ",${srcId}" + dofSuf, dof12)

val affineBegin = EmptyTask() set(
    inputs  += (srcId, srcIm, dof6),
    outputs += (srcId, srcIm, dof6, dof12)
  ) source affineOutputFile

val affineReg = ScalaTask(
  """IRTK.ireg(Settings.refIm, srcIm, Some(dof6), dof12,
    |  "No. of threads"       -> 8,
    |  "Transformation model" -> "Affine",
    |  "Background value"     -> 0,
    |  "Padding value"        -> 0
    |)
  """.stripMargin) set(
    imports     += "com.andreasschuh.repeat._",
    usedClasses += (Settings.getClass(), IRTK.getClass()),
    inputs      += (srcId, srcIm, dof6, dof12),
    outputs     += (srcId, srcIm,       dof12)
  )

val affineEnd = StrainerCapsule(EmptyTask())

val affineMole = affineBegin -- (((affineReg on env) -- affineEnd) when "!dof12.exists()", affineEnd when "dof12.exists()")

// Run spatial normalization pipeline for each input image
val mole = (forEachIm -- rigidMole) + (rigidEnd -- affineMole) start
mole.waitUntilEnded()
