ThisBuild / version := "1.0.0"
ThisBuild / sbtPlugin := false

lazy val root = (project in file("."))
  .settings(
    name := "fetter",
    scalaVersion := "2.13.10",
    libraryDependencies ++= List(
      "org.scala-lang" % "scala-compiler" % "2.13.10",
      "org.scalatest" %% "scalatest" % "3.2.15" % Test,
    ),
    scalacOptions += "-Werror",
    Test / scalacOptions ++= {
      val jar = (Compile / packageBin).value
      Seq(s"-Xplugin:${jar.getAbsolutePath}", s"-Jdummy=${jar.lastModified}") // ensures recompile
    },
  )
