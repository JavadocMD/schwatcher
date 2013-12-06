package com.beachape.filemanagement

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestActorRef, TestKit}
import com.beachape.filemanagement.Messages._
import com.beachape.filemanagement.RegistryTypes.Callback
import java.nio.file.StandardWatchEventKinds._
import java.nio.file.{Files, Path, WatchEvent}
import org.scalatest.BeforeAndAfter
import org.scalatest.FunSpec
import org.scalatest.PrivateMethodTester
import org.scalatest.matchers.ShouldMatchers
import java.io.{FileWriter, BufferedWriter}
import scala.concurrent.duration._
import com.beachape.filemanagement.MonitorActor.CallbackRegistryMap

class MonitorActorSpec extends TestKit(ActorSystem("testSystem"))
with FunSpec
with ShouldMatchers
with BeforeAndAfter
with ImplicitSender
with PrivateMethodTester {

  trait Fixtures {
    val emptyCbMap = MonitorActor.initialEventTypeCallbackRegistryMap

    // Actor
    val monitorActorRef = TestActorRef(new MonitorActor)
    val monitorActor = monitorActorRef.underlyingActor

    // Files
    val tempFile = java.io.File.createTempFile("fakeFile", ".log")
    val tempDirPath = Files.createTempDirectory("root")
    val tempDirLevel1Path = Files.createTempDirectory(tempDirPath, "level1")
    val tempDirLevel2Path = Files.createTempDirectory(tempDirLevel1Path, "level2")
    val tempFileInTempDir = Files.createTempFile(tempDirPath, "hello", ".there")

    // Make sure the files get deleted on exit
    tempFile.deleteOnExit()
    tempDirPath.toFile.deleteOnExit()
    tempDirLevel1Path.toFile.deleteOnExit()
    tempDirLevel2Path.toFile.deleteOnExit()
    tempFileInTempDir.toFile.deleteOnExit()

    val dummyFunction: Path => Unit = {
      (path: Path) => val bleh = "lala"
    }
    val newRegistryMap = PrivateMethod[CallbackRegistryMap]('newCallbackRegistryMap)

    // Test helper methods
    def addCallbackFor(originalMap: CallbackRegistryMap,
                       path: Path,
                       event: WatchEvent.Kind[Path],
                       callback: Callback,
                       recursive: Boolean = false): CallbackRegistryMap = {
      val absolutePath = path.toAbsolutePath
      monitorActor invokePrivate newRegistryMap(
        originalMap,
        event, { registry: CallbackRegistry => registry.withCallbackFor(absolutePath, callback, recursive)})
    }

    def removeCallbacksFor(originalMap: CallbackRegistryMap,
                           path: Path,
                           event: WatchEvent.Kind[Path],
                           recursive: Boolean = false): CallbackRegistryMap = {
      val absolutePath = path.toAbsolutePath
      monitorActor invokePrivate newRegistryMap(
      originalMap,
      event, { registry: CallbackRegistry => registry.withoutCallbacksFor(absolutePath, recursive)})
    }

    // Recursive version of the same as above
    def addCallbackFor(originalMap: CallbackRegistryMap,
                       pathsCallbackRecursive: List[(Path, WatchEvent.Kind[Path], Callback, Boolean)]): CallbackRegistryMap = {
      pathsCallbackRecursive.foldLeft(originalMap) {
        case (memo, (path, eventType, callback, recursive)) => addCallbackFor(memo, path, eventType, callback, recursive)
      }
    }

  }

  describe("construction via Props factory") {

    it("should throw an error when concurrency parameter is set to less than 1") {
      val thrown = intercept[IllegalArgumentException] {
        TestActorRef(MonitorActor(0))
      }
      thrown.getMessage should be("requirement failed: Callback concurrency requested is 0 but it should at least be 1")
    }

    it("should not throw an error when concurrency parameter is set to 1") {
      TestActorRef(MonitorActor(1))
      true should be(true)
    }


    it("should not throw an error when concurrency parameter is set to > 1") {
      TestActorRef(MonitorActor(2))
      true should be(true)
    }

  }

  describe("methods testing") {

    describe("#modifyCallbackRegistry") {

      it("should allow callbacks to be added to the registry") {
        new Fixtures {
          val map = addCallbackFor(emptyCbMap, tempFile.toPath, ENTRY_CREATE, dummyFunction)
          monitorActor.callbacksFor(map, ENTRY_CREATE, tempFile.toPath).isEmpty should be(false)
          monitorActor.callbacksFor(map, ENTRY_CREATE, tempFile.toPath) foreach {
            callbacks =>
              callbacks should contain(dummyFunction)
          }
        }
      }

      it("should allow callbacks to be removed from the registry") {
        new Fixtures {
          val addedMap = addCallbackFor(emptyCbMap, tempFile.toPath, ENTRY_CREATE, dummyFunction)
          val removedMap = removeCallbacksFor(addedMap, tempFile.toPath, ENTRY_CREATE)
          monitorActor.callbacksFor(removedMap, ENTRY_CREATE, tempFile.toPath).isEmpty should be(true)
        }
      }
    }

    describe("adding callbacks recursively") {

      it("should add callbacks for all folders that exist under the path given") {
        new Fixtures {
          val addedMap = addCallbackFor(emptyCbMap, tempDirPath, ENTRY_CREATE, dummyFunction, true)
          monitorActor.callbacksFor(addedMap, ENTRY_CREATE, tempDirLevel1Path).map(callbacks =>
            callbacks should contain(dummyFunction))
          monitorActor.callbacksFor(addedMap, ENTRY_CREATE, tempDirLevel2Path).map(callbacks =>
            callbacks should contain(dummyFunction))
        }
      }

      it("should add callbacks for a file path") {
        new Fixtures {
          val addedMap = addCallbackFor(emptyCbMap, tempFileInTempDir, ENTRY_CREATE, dummyFunction, true)
          monitorActor.callbacksFor(addedMap, ENTRY_CREATE, tempFileInTempDir).map(callbacks =>
            callbacks should contain(dummyFunction))
        }
      }

      it("should not add callbacks recursively if given a file path") {
        new Fixtures {
          val addedMap = addCallbackFor(emptyCbMap, tempFileInTempDir, ENTRY_CREATE, dummyFunction, true)
          monitorActor.callbacksFor(addedMap, ENTRY_CREATE, tempDirLevel1Path).map(callbacks =>
            callbacks should not contain dummyFunction)
          monitorActor.callbacksFor(addedMap, ENTRY_CREATE, tempDirLevel2Path).map(callbacks =>
            callbacks should not contain dummyFunction)
        }
      }

    }

    describe("recursively removing callbacks") {


      it("should remove callbacks for all folders that exist under the path given") {
        new Fixtures {
          val addedMap = addCallbackFor(emptyCbMap, tempDirPath, ENTRY_CREATE, dummyFunction, true)
          val removedMap = removeCallbacksFor(addedMap, tempDirPath, ENTRY_CREATE, true)
          monitorActor.callbacksFor(removedMap, ENTRY_CREATE, tempDirLevel1Path).isEmpty should be(true)
          monitorActor.callbacksFor(removedMap, ENTRY_CREATE, tempDirLevel2Path).isEmpty should be(true)
        }
      }

      it("should remove callbacks for a file path") {
        new Fixtures {
          val addedMap = addCallbackFor(emptyCbMap, tempFileInTempDir, ENTRY_CREATE, dummyFunction, true)
          val removedMap = removeCallbacksFor(addedMap, tempFileInTempDir, ENTRY_CREATE, true)
          monitorActor.callbacksFor(removedMap, ENTRY_CREATE, tempFileInTempDir).isEmpty should be(true)
        }
      }

    }

    describe("#callbacksFor") {


      it("should return Some[Callbacks] that contains prior registered callbacks for a path") {
        new Fixtures {
          val cbMap = addCallbackFor(emptyCbMap, tempFile.toPath, ENTRY_CREATE, dummyFunction)
          monitorActor.callbacksFor(cbMap, ENTRY_CREATE, tempFile.toPath) foreach {
            callbacks =>
              callbacks should contain(dummyFunction)
          }
        }
      }

      it("should return Some[Callbacks] that does not contain callbacks for paths never registered") {
        new Fixtures {
          val tempFile2 = java.io.File.createTempFile("fakeFile2", ".log")
          tempFile2.deleteOnExit()
          monitorActor.callbacksFor(emptyCbMap, ENTRY_CREATE, tempFile2.toPath).isEmpty should be(true)
        }
      }

    }

    describe("#processCallbacksFor") {

      val callback = {
        path: Path => testActor ! path
      }

      it("should get the proper callback for a file path") {
        new Fixtures {
          val cbMap = addCallbackFor(emptyCbMap, List(
            (tempDirPath, ENTRY_CREATE, callback, false),
            (tempDirLevel2Path, ENTRY_CREATE, callback, false),
            (tempFileInTempDir, ENTRY_CREATE, callback, false)
          ))
          monitorActor.processCallbacksFor(cbMap, ENTRY_CREATE, tempFileInTempDir)
          /*
            Fired twice because tempDirPath and tempFileInTempDir are both registered.
            The file path passed to the callback is still the same though because it
            is still the file
          */
          expectMsgAllOf(tempFileInTempDir, tempFileInTempDir)
        }
      }

      it("should get the proper callback for a directory") {
        new Fixtures {
          val cbMap = addCallbackFor(emptyCbMap, List(
            (tempDirPath, ENTRY_MODIFY, callback, false),
            (tempDirLevel2Path, ENTRY_MODIFY, callback, false),
            (tempFileInTempDir, ENTRY_MODIFY, callback, false)
          ))
          monitorActor.processCallbacksFor(cbMap, ENTRY_MODIFY, tempDirPath)
          expectMsg(tempDirPath)
        }
      }

    }
//
//    describe("messaging tests") {
//
//      describe("RegisterCallback message type") {
//
//        val callbackFunc = {
//          (path: Path) => val receivedPath = path
//        }
//
//        it("should register a callback when given a file path") {
//          new Fixtures {
//            val registerFileCallbackMessage = RegisterCallback(ENTRY_CREATE, recursive = false, tempFileInTempDir, callbackFunc)
//            monitorActorRef ! registerFileCallbackMessage
//            val Some(callbacks) = monitorActor.callbacksFor(ENTRY_CREATE, tempFileInTempDir)
//            callbacks.contains(callbackFunc) should be(true)
//          }
//
//        }
//
//        it("should register a callback when given a directory path") {
//          new Fixtures {
//            val registerFileCallbackMessage = RegisterCallback(ENTRY_MODIFY, recursive = false, tempDirPath, callbackFunc)
//            monitorActorRef ! registerFileCallbackMessage
//            val Some(callbacks) = monitorActor.callbacksFor(ENTRY_MODIFY, tempDirPath)
//            callbacks.contains(callbackFunc) should be(true)
//          }
//        }
//
//        it("should register a callback recursively for a directory path") {
//          new Fixtures {
//            val registerFileCallbackMessage = RegisterCallback(ENTRY_DELETE, recursive = true, tempDirPath, callbackFunc)
//            monitorActorRef ! registerFileCallbackMessage
//            val Some(callbacksForTempDirLevel1) = monitorActor.callbacksFor(ENTRY_DELETE, tempDirLevel1Path)
//            callbacksForTempDirLevel1.contains(callbackFunc) should be(true)
//            val Some(callbacksForTempDirLevel2) = monitorActor.callbacksFor(ENTRY_DELETE, tempDirLevel2Path)
//            callbacksForTempDirLevel2.contains(callbackFunc) should be(true)
//          }
//        }
//
//        it("should not register a callback for a file inside a directory tree even when called recursively") {
//          new Fixtures {
//            val registerFileCallbackMessage = RegisterCallback(ENTRY_CREATE, recursive = true, tempDirPath, callbackFunc)
//            monitorActorRef ! registerFileCallbackMessage
//            monitorActor.callbacksFor(ENTRY_CREATE, tempFileInTempDir).isEmpty should be(true)
//          }
//        }
//
//      }
//
//      describe("UnRegisterCallback message type") {
//
//        val callbackFunc = {
//          (path: Path) => val receivedPath = path
//        }
//
//        it("should un-register a callback when given a file path") {
//          new Fixtures {
//            monitorActorRef ! RegisterCallback(ENTRY_CREATE, recursive = false, tempFileInTempDir, callbackFunc)
//            monitorActorRef ! UnRegisterCallback(ENTRY_CREATE, recursive = false, tempFileInTempDir)
//            monitorActor.callbacksFor(ENTRY_CREATE, tempFileInTempDir).isEmpty should be(true)
//          }
//
//        }
//
//        it("should un-register a callback when given a directory path") {
//          new Fixtures {
//            monitorActorRef ! RegisterCallback(ENTRY_DELETE, recursive = false, tempDirPath, callbackFunc)
//            monitorActorRef ! UnRegisterCallback(ENTRY_DELETE, recursive = false, tempDirPath)
//            monitorActor.callbacksFor(ENTRY_DELETE, tempDirPath).isEmpty should be(true)
//          }
//        }
//
//        it("should un-register a callback recursively for a directory path") {
//          new Fixtures {
//            monitorActorRef ! RegisterCallback(ENTRY_MODIFY, recursive = true, tempDirPath, callbackFunc)
//            monitorActorRef ! UnRegisterCallback(ENTRY_MODIFY, recursive = true, tempDirPath)
//            monitorActor.callbacksFor(ENTRY_MODIFY, tempDirLevel1Path).isEmpty should be(true)
//            monitorActor.callbacksFor(ENTRY_MODIFY, tempDirLevel2Path).isEmpty should be(true)
//          }
//        }
//
//        it("should not un-register a callback for a file inside a directory tree even when called recursively") {
//          new Fixtures {
//            monitorActorRef ! RegisterCallback(ENTRY_CREATE, recursive = true, tempDirPath, callbackFunc)
//            monitorActorRef ! RegisterCallback(ENTRY_CREATE, recursive = true, tempFileInTempDir, callbackFunc)
//            monitorActorRef ! UnRegisterCallback(ENTRY_CREATE, recursive = true, tempDirPath)
//            monitorActor.callbacksFor(ENTRY_CREATE, tempFileInTempDir).isEmpty should be(false)
//          }
//        }
//
//      }

//      describe("EventAtPath message type") {
//
//        sealed case class TestResponse(message: String)
//        val callbackFunc = {
//          (path: Path) => testActor ! TestResponse(s"path is $path")
//        }
//
//        it("should cause callback to be fired for a registered file path") {
//          new Fixtures {
//            val registerFileCallbackMessage = RegisterCallback(ENTRY_CREATE, recursive = false, tempFileInTempDir, callbackFunc)
//            monitorActorRef ! registerFileCallbackMessage
//            monitorActorRef ! EventAtPath(ENTRY_CREATE, tempFileInTempDir)
//            expectMsg(TestResponse(s"path is $tempFileInTempDir"))
//          }
//        }
//
//        it("should cause callback to be fired for a registered directory path") {
//          new Fixtures {
//            val registerFileCallbackMessage = RegisterCallback(ENTRY_MODIFY, recursive = false, tempDirPath, callbackFunc)
//            monitorActorRef ! registerFileCallbackMessage
//            monitorActorRef ! EventAtPath(ENTRY_MODIFY, tempFileInTempDir)
//            expectMsg(TestResponse(s"path is $tempFileInTempDir"))
//          }
//        }
//
//        it("should cause callbacks to to fired for a registered directory path AND the file path itself") {
//          new Fixtures {
//            val registerFileCallbackMessageDirectory = RegisterCallback(ENTRY_DELETE, recursive = false, tempDirPath,
//              path => testActor ! TestResponse(s"directory callback path is $path"))
//            val registerFileCallbackMessageFile = RegisterCallback(ENTRY_DELETE, recursive = false, tempFileInTempDir,
//              path => testActor ! TestResponse(s"file callback path is $path"))
//            monitorActorRef ! registerFileCallbackMessageDirectory
//            monitorActorRef ! registerFileCallbackMessageFile
//            monitorActorRef ! EventAtPath(ENTRY_DELETE, tempFileInTempDir)
//            expectMsgAllOf(
//              TestResponse(s"directory callback path is $tempFileInTempDir"),
//              TestResponse(s"file callback path is $tempFileInTempDir"))
//          }
//        }
//
//      }
//
//    }
//
  }

  describe("integration testing") {

    it("should fire the appropriate callback if a monitored file has been modified") {
      new Fixtures {
        val register = RegisterCallback(ENTRY_MODIFY, recursive = false, tempFile.toPath,
          path => testActor ! s"Modified file is at $path")
        monitorActorRef ! register
        // Sleep to make sure that the Java WatchService is monitoring the file ...
        Thread.sleep(3000)
        val writer = new BufferedWriter(new FileWriter(tempFile))
        writer.write("There's text in here wee!!")
        writer.close
        // Within 60 seconds is used in case the Java WatchService is acting slow ...
        within(60 seconds) {
          expectMsg(s"Modified file is at ${tempFile.toPath}")
        }
      }
    }

  }

}
