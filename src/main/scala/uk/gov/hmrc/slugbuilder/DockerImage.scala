package uk.gov.hmrc.slugbuilder

import cats.data.EitherT

import scala.concurrent.Future

class DockerImage {
  def create(repositoryName: RepositoryName, releaseVersion: ReleaseVersion): EitherT[Future, String, String] = ???

}
