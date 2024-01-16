import sbt.Keys._
import sbt._

lazy val slugBuilder = Project("slug-builder", file("."))
  .settings(
    majorVersion := 0,
    isPublicArtefact := true,
    libraryDependencies ++= compileDependencies ++ testDependencies,
    scalaVersion := "2.13.12",
    assemblySettings
  )

val compileDependencies = Seq(
  "org.playframework"  %% "play-ahc-ws-standalone" % "3.0.1",
  "org.playframework"  %% "play-json"              % "3.0.1",
  "log4j"              %  "log4j"                  % "1.2.17",
  "org.apache.commons" %  "commons-compress"       % "1.21",
  "org.slf4j"          %  "slf4j-api"              % "1.7.32",
  "org.slf4j"          %  "slf4j-log4j12"          % "1.7.32",
  "org.typelevel"      %% "cats-core"              % "2.10.0"
)

val testDependencies = Seq(
  "org.scalatestplus"    %% "scalacheck-1-17"          % "3.2.17.0"    % Test,
  "org.scalatest"        %% "scalatest"                % "3.2.17"      % Test,
  "org.mockito"          %% "mockito-scala-scalatest"  % "1.17.12"     % Test,
  "org.scalamock"        %% "scalamock"                % "5.2.0"       % Test, // TODO scalamock and mockito!?
  "com.vladsch.flexmark" %  "flexmark-all"             % "0.64.8"      % Test
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
