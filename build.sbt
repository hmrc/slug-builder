import sbt.Keys._
import sbt._

lazy val slugBuilder = Project("slug-builder", file("."))
  .settings(
    majorVersion := 0,
    isPublicArtefact := true,
    libraryDependencies ++= compileDependencies ++ testDependencies,
    scalaVersion := "2.13.8",
    assemblySettings
  )

val compileDependencies = Seq(
  "com.typesafe.play"  %% "play-ahc-ws-standalone" % "2.1.11",
  "com.typesafe.akka"  %% "akka-stream"            % "2.6.21", // to fix binary incompatibility between 0.9.0 and 1.0.2 of scala-java8-compat
  "com.typesafe.play"  %% "play-json"              % "2.9.4",
  "log4j"              %  "log4j"                  % "1.2.17",
  "org.apache.commons" %  "commons-compress"       % "1.21",
  "org.eclipse.jgit"   %  "org.eclipse.jgit"       % "4.11.0.201803080745-r",
  "org.slf4j"          %  "slf4j-api"              % "1.7.32",
  "org.slf4j"          %  "slf4j-log4j12"          % "1.7.32",
  "org.typelevel"      %% "cats-core"              % "2.10.0"
)

val testDependencies = Seq(
  "org.scalatestplus"    %% "scalatestplus-scalacheck" % "3.1.0.0-RC2" % Test,
  "org.scalatest"        %% "scalatest"                % "3.2.15"      % Test,
  "org.mockito"          %% "mockito-scala-scalatest"  % "1.17.12"     % Test,
  "org.scalamock"        %% "scalamock"                % "5.2.0"       % Test, // TODO scalamock and mockito!?
  "com.vladsch.flexmark" %  "flexmark-all"             % "0.64.6"      % Test
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
