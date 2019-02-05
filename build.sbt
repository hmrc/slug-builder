import sbt.Keys._
import sbt._
import uk.gov.hmrc.DefaultBuildSettings._
import uk.gov.hmrc.SbtArtifactory
import uk.gov.hmrc.versioning.SbtGitVersioning

val appName: String = "slug-builder"

lazy val slugBuilder = Project(appName, file("."))
  .enablePlugins(Seq(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory): _*)
  .settings(scalaSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(
    majorVersion := 0,
    makePublicallyAvailableOnBintray := true,
    libraryDependencies ++= compileDependencies ++ testDependencies,
    retrieveManaged := true,
    assemblySettings,
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false)
  )

val compileDependencies = Seq(
  "com.typesafe.play"  %% "play-ahc-ws-standalone" % "1.1.2",
  "com.typesafe.play"  %% "play-json"              % "2.6.13",
  "log4j"              % "log4j"                   % "1.2.17",
  "org.apache.commons" % "commons-compress"        % "1.16.1",
  "org.eclipse.jgit"   % "org.eclipse.jgit"        % "4.11.0.201803080745-r",
  "org.slf4j"          % "slf4j-api"               % "1.7.25",
  "org.slf4j"          % "slf4j-log4j12"           % "1.7.25",
  "org.typelevel"      %% "cats-core"              % "1.0.1"
)

val testDependencies = Seq(
  "org.mockito"    % "mockito-core" % "2.18.3" % Test,
  "org.pegdown"    % "pegdown"      % "1.6.0"  % Test,
  "org.scalacheck" %% "scalacheck"  % "1.14.0" % Test,
  "org.scalamock"  %% "scalamock"   % "4.1.0"  % Test,
  "org.scalatest"  %% "scalatest"   % "3.0.5"  % Test
)

val assemblySettings = Seq(
  test in assembly := {},
  assemblyMergeStrategy in assembly := {
    case PathList("META-INF", xs @ _*)                                                           => MergeStrategy.discard
    case PathList("org", "apache", "commons", "logging", xs @ _*)                                => MergeStrategy.first
    case PathList("play", "core", "server", xs @ _*)                                             => MergeStrategy.first
    case PathList("javax", "inject", xs @ _*)                                                    => MergeStrategy.first
    case PathList("io", "netty", "netty-codec-http", xs @ _*)                                    => MergeStrategy.first
    case PathList("org", "apache", "http", "impl", "io", "ChunkedInputStream.class")             => MergeStrategy.first
    case PathList("org", "newsclub", "net", "unix", "AFUNIXSocketImpl$AFUNIXInputStream.class")  => MergeStrategy.first
    case PathList("org", "newsclub", "net", "unix", "AFUNIXSocketImpl$AFUNIXOutputStream.class") => MergeStrategy.first
    case PathList("org", "newsclub", "net", "unix", "AFUNIXSocketImpl$Lenient.class")            => MergeStrategy.first
    case PathList("org", "newsclub", "net", "unix", "AFUNIXSocketImpl.class")                    => MergeStrategy.first
    case x                                                                                       => (assemblyMergeStrategy in assembly).value(x)
  },
  artifact in (Compile, assembly) := {
    val art = (artifact in (Compile, assembly)).value
    art.copy(`classifier` = Some("assembly"))
  }
)

addArtifact(artifact in (Compile, assembly), assembly)
