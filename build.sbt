import sbt._
import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, scalaSettings}
import uk.gov.hmrc.SbtArtifactory
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.versioning.SbtGitVersioning.majorVersion

val appName: String = "slug-builder"

lazy val slugBuilder = Project(appName, file("."))
  .enablePlugins(Seq(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory): _*)
  .settings(scalaSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(
    majorVersion := 0,
    scalaVersion := "2.11.11",
    libraryDependencies ++= compile ++ test,
    retrieveManaged := true,
    evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    resolvers += Resolver.bintrayRepo("hmrc", "releases"),
    resolvers += Resolver.jcenterRepo
  )

val compile = Seq(
  "org.typelevel"     %% "cats-core" % "1.0.1",
  "com.typesafe.play" %% "play-ws"   % "2.5.12"
)

val test = Seq(
  "org.scalatest"  %% "scalatest"  % "3.0.5"  % Test,
  "org.scalacheck" %% "scalacheck" % "1.14.0" % Test,
  "org.pegdown"    % "pegdown"     % "1.4.2"  % Test,
  "org.scalamock"  %% "scalamock"  % "4.1.0"  % Test
)
