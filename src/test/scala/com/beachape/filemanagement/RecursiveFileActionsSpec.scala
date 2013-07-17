package com.beachape.filemanagement

import org.scalatest.{PrivateMethodTester, BeforeAndAfter, FunSpec}
import org.scalatest.matchers.ShouldMatchers
import java.nio.file.{Path, Paths, Files}

class RecursiveFileActionsSpec extends FunSpec
  with ShouldMatchers
  with BeforeAndAfter {

  class DummyClass extends RecursiveFileActions

  val dummy = new DummyClass

  val tempDirPath = Files.createTempDirectory("root")
  val tempDirLevel1Path = Files.createTempDirectory(tempDirPath, "level1")
  val tempDirLevel2Path = Files.createTempDirectory(tempDirLevel1Path, "level2")
  val tempFileInTempDir = Files.createTempFile(tempDirPath, "hello", ".there")

  describe("#recursiveActionForPath") {

    it("should ignore paths for files") {
      var called = false
      dummy.recursiveActionForPath(tempFileInTempDir){(path, attr) =>
        called = true
      }
      called should be(false)
    }

    it("should call the block once for every existing directory inside a directory path") {
      var counter = 0
      dummy.recursiveActionForPath(tempDirPath){(path, attr) =>
        counter += 1
      }
      counter should be(3)
    }
  }

}
