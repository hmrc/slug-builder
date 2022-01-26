import sbt.Keys._
import sbt._

lazy val slugBuilder = Project("slug-builder", file("."))
  .settings(
    majorVersion := 0,
    isPublicArtefact := true,
    libraryDependencies ++= compileDependencies ++ testDependencies,
    scalaVersion := "2.13.7",
    assemblySettings,
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false)
  )

val compileDependencies = Seq(
  "com.typesafe.play"  %% "play-ahc-ws-standalone" % "2.1.7",
  "com.typesafe.play"  %% "play-json"              % "2.9.2",
  "log4j"              %  "log4j"                  % "1.2.17",
  "org.apache.commons" %  "commons-compress"       % "1.16.1",
  "org.eclipse.jgit"   %  "org.eclipse.jgit"       % "4.11.0.201803080745-r",
  "org.slf4j"          %  "slf4j-api"              % "1.7.25",
  "org.slf4j"          %  "slf4j-log4j12"          % "1.7.25",
  "org.typelevel"      %% "cats-core"              % "2.7.0"
)

val testDependencies = Seq(
  "org.scalatestplus"    %% "scalatestplus-scalacheck" % "3.1.0.0-RC2" % Test,
  "org.scalatest"        %% "scalatest"                % "3.2.3"       % Test,
  "org.mockito"          %% "mockito-scala-scalatest"  % "1.16.46"     % Test,
  "org.scalamock"        %% "scalamock"                % "5.2.0"       % Test, // TODO scalamock and mockito!?
  "com.vladsch.flexmark" %  "flexmark-all"             % "0.35.10"     % Test
)

val assemblySettings = Seq(
  assembly / test := {},
  assembly / assemblyMergeStrategy := {
    case PathList("META-INF", xs @ _*)   => MergeStrategy.discard
    case PathList("module-info.class")   => MergeStrategy.discard // from com/fasterxml/jackson libraries
    case x                               => (assembly / assemblyMergeStrategy).value(x)
  },
  Compile / assembly / artifact := {
    val art = (Compile / assembly / artifact).value
    art.withClassifier(Some("assembly"))
  }
)

addArtifact(Compile / assembly / artifact, assembly)
