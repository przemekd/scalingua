import sbt.{Def, _}
import Keys._
import sbt.plugins.JvmPlugin

object ParserGenerator extends AutoPlugin {
  object autoImport {
    val generateLexer   = taskKey[Seq[File]]("Generate JFlex lexer")
    val generateParser  = taskKey[Seq[File]]("Generate CUP parser")
  }

  import autoImport._

  override def requires = JvmPlugin

  override def projectSettings: Seq[Def.Setting[_]] =
    inConfig(Compile)(configurationSettings) ++ inConfig(Test)(configurationSettings)

  private def generationSettings(extension: String, dir: String, task: TaskKey[Seq[File]]): Seq[Def.Setting[_]] = Seq(
    sourceDirectories in task := Seq(sourceDirectory.value),
    includeFilter in task := extension,
    excludeFilter in task := HiddenFileFilter,

    sources in task :=
      Defaults.collectFiles(sourceDirectories in task, includeFilter in task, excludeFilter in task).value,

    target in task := target.value / dir,

    sourceGenerators += task.taskValue,
    managedSourceDirectories += (target in task).value
  )

  private def configurationSettings: Seq[Def.Setting[_]] =
    generationSettings("*.flex", "generated-lexer", generateLexer) ++
    generationSettings("*.cup", "generated-parser", generateParser) ++
    Seq(
      generateLexer := generateLexerTask.value,
      generateParser := generateParserTask.value
    )

  private def runJava(mainClass: Class[_], args: String*): Unit = {
    val loc = new File(mainClass.getProtectionDomain.getCodeSource.getLocation.toURI).getCanonicalPath
    val cmdline = Seq("java", "-cp", loc, mainClass.getName) ++ args
    val exitCode = cmdline.!
    if (exitCode != 0)
      throw new RuntimeException(s"Process '${cmdline.mkString(" ")}' exited with code $exitCode")
  }

  private def process(src: TaskKey[Seq[File]], tgt: SettingKey[File])(f: (File, File) => Unit) = Def.task[Seq[File]] {
    val srcFiles = src.value
    val targetDir = tgt.value

    if (targetDir.exists()) IO.delete(targetDir)

    if (srcFiles.isEmpty) Nil
    else {
      IO.createDirectory(targetDir)
      srcFiles.foreach(f(_, targetDir))
      IO.listFiles(targetDir)
    }
  }

  def generateLexerTask =
    process(sources in generateLexer, target in generateLexer) { (f, t) =>
      runJava(classOf[jflex.Main], f.getCanonicalPath, "-d", t.getCanonicalPath)
    }

  def generateParserTask =
    process(sources in generateParser, target in generateParser) { (f, t) =>
      runJava(classOf[java_cup.Main], "-destdir", t.getCanonicalPath, "-locations", f.getCanonicalPath)
    }
}