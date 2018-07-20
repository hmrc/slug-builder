resolvers += Resolver.url("hmrc-sbt-plugin-releases", url("https://dl.bintray.com/hmrc/sbt-plugin-releases"))(
  Resolver.ivyStylePatterns)

resolvers += Resolver.url(
  "hmrc-sbt-plugin-release-candidates",
  url("https://dl.bintray.com/hmrc/sbt-plugin-release-candidates"))(Resolver.ivyStylePatterns)

resolvers += "Typesafe Releases" at "http://repo.typesafe.com/typesafe/releases/"

addSbtPlugin("uk.gov.hmrc" % "sbt-auto-build" % "1.12.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-git-versioning" % "1.7.0")

addSbtPlugin("uk.gov.hmrc" % "sbt-artifactory" % "0.12.0")

addSbtPlugin("com.eed3si9n" % "sbt-assembly" % "0.14.6")
