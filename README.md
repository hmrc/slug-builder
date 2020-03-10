
# slug-builder

 [ ![Download](https://api.bintray.com/packages/hmrc/releases/slug-builder/images/download.svg) ](https://bintray.com/hmrc/releases/slug-builder/_latestVersion)

This is a tool to be used in command line to create and publish *slug* artifacts for a given microservice.

# How to create an executable fat jar?

`sbt assembly`

# How to create and publish a slug?

The following environment variables have to be set:
* `JAVA_VERSION`
* `ARTIFACTORY_URI`
* `WEBSTORE_URI`
* `SLUG_RUNNER_VERSION`
* `GITHUB_API_USER`
* `GITHUB_API_TOKEN`

The following environment variables are optional:
* `INCLUDE_FILES` - a comma separed list of file paths to be included in the root of the slug

Once the variables are set and a fat jar is created, slug can be built by issuing a command:

`java -jar path-to-slug-builder-fat-jar repo-name x.x.x`

# Publishing a fat jar

At the moment the assembly task is not executed on `publish` or `publish-local` and it's not recommended to publish fat jars (more on that [here](https://github.com/sbt/sbt-assembly#publishing-not-recommended)). However, this can be changed as described in the *sbt-assembly* documentation.

### License

This code is open source software licensed under the [Apache 2.0 License]("http://www.apache.org/licenses/LICENSE-2.0.html").
