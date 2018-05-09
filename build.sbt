
import sbt._
import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, scalaSettings}
import uk.gov.hmrc.SbtArtifactory
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.versioning.SbtGitVersioning.majorVersion

val appName: String = "slug-builder"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(SbtAutoBuildPlugin, SbtGitVersioning, SbtArtifactory): _*)
  .settings(scalaSettings: _*)
  .settings(defaultSettings(): _*)
  .settings(
    majorVersion                                  := 0,
    scalaVersion                                  := "2.11.11",
    libraryDependencies                           ++= compile ++ test,
    retrieveManaged                               := true,
    evictionWarningOptions in update              := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
    resolvers                                     += Resolver.bintrayRepo("hmrc", "releases"),
    resolvers                                     += Resolver.jcenterRepo
  )


val compile = Seq(
)

val test  = Seq(
    "org.scalatest"          %% "scalatest"          % "3.0.5"              % Test
)
