/*
 * Copyright 2022 HM Revenue & Customs
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

import org.scalatest.{Matchers, WordSpec}

class CliToolsSpec extends WordSpec with Matchers {

  "run" should {
    "execute a commandline" in new TestSetup {
      new CliTools(progressReporter).run(Array("echo", "Hello world")) should be('right)
      progressReporter.logs                                            should contain("Hello world")
    }

    "return handle errors where the command is not found" in new TestSetup {
      val invalidCmd = "invalidCmd"
      new CliTools(progressReporter).run(Array(invalidCmd)) should be('left)
      progressReporter.logs                                 shouldBe empty
    }

    "return the exit code and error if it fails to execute the command" in new TestSetup {
      new CliTools(progressReporter).run(Array("ls", "some-non-existing-file")) should be('left)
      progressReporter.logs                                                     should not be empty
    }
  }

  trait TestSetup {

    val progressReporter = new ProgressReporterStub()
  }
}
