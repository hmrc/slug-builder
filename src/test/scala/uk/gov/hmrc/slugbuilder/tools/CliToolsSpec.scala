/*
 * Copyright 2023 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.slugbuilder.tools

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class CliToolsSpec
  extends AnyWordSpec
     with Matchers
     with EitherValues {

  "run" should {
    "execute a commandline" in new TestSetup {
      new CliTools(progressReporter).run(Array("echo", "Hello world")).value shouldBe ()
      progressReporter.logs                                            should contain("Hello world")
    }

    "return handle errors where the command is not found" in new TestSetup {
      val invalidCmd = "invalidCmd"
      new CliTools(progressReporter).run(Array(invalidCmd)).left.value shouldBe "Cannot run program \"invalidCmd\": error=2, No such file or directory"
      progressReporter.logs                                 shouldBe empty
    }

    "return the exit code and error if it fails to execute the command" in new TestSetup {
      new CliTools(progressReporter).run(Array("ls", "some-non-existing-file")).left.value shouldBe "Command 'ls some-non-existing-file' failed with exit code 2"
      progressReporter.logs                                                     should not be empty
    }
  }

  trait TestSetup {
    val progressReporter = new ProgressReporterStub()
  }
}
