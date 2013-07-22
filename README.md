Schwatcher [![Build Status](https://travis-ci.org/lloydmeta/schwatcher.png?branch=ci/add_travis)](https://travis-ci.org/lloydmeta/schwatcher)
==========

__Note__: Requires Java7 because the [WatchService API](http://docs.oracle.com/javase/7/docs/api/java/nio/file/WatchService.html)
is an essential part of this library.

__TL;DR__ A library that wraps the [WatchService API](http://docs.oracle.com/javase/7/docs/api/java/nio/file/WatchService.html)
of Java7 and allows callbacks to be registered on both directories and files.

As of Java7, the WatchService API was introduced to allow developers to monitor the file system without resorting to an
external library/dependency like [JNotify](http://jnotify.sourceforge.net/). Unfortunately, it requires you to use a loop
that blocks in order to retrieve events from the API.

Schwatcher will allow you to instantiate an Actor, register callbacks (functions that take a Path and return Unit) to be
fired for certain [Path](http://docs.oracle.com/javase/7/docs/api/java/nio/file/Path.html)s and [event types](http://docs.oracle.com/javase/7/docs/api/java/nio/file/StandardWatchEventKinds.html),
then simply wait for the callbacks to be fired. The goal of Schwatcher is to facilitate the use of the Java7 API in Scala in
a simple way that is in line with the functional programming paradigm.

Note:

1. Callbacks are registered for specific paths and for directory paths can be registered as recursive so that a single
   callback is fired when an event occurs inside the directory tree.
2. Callbacks are not checked for uniqueness when registered to a specific path.
3. A specific path can have multiple callbacks registered to a file [event types](http://docs.oracle.com/javase/7/docs/api/java/nio/file/StandardWatchEventKinds.html),
   all will be fired.
4. Callbacks are not guaranteed to be fired in any particular order unless if concurrency is set to 1 on the MonitorActor.
5. Any event on a file path will bubble up to its immediate parent folder path. This means that if both a file and it's
   parent directory are registered for callbacks, both set of callbacks will be fired.

As a result of note 5, you may want to think twice about registering recursive callbacks for `ENTRY_DELETE` because if a
directory is deleted within a directory, 2 callbacks will be fired, once for the deleted directory and once for the directory
above it.

Installation
------------

Add the following to your `build.sbt`

```scala
libraryDependencies += "com.beachape.filemanagement" %% "schwatcher" % "0.0.1"
```

If the above does not work because it cannot be resolved, its likely because it hasn't been synced to Maven central yet.
In that case, download a SNAPSHOT release of the same version by adding this to `build.sbt`

```
resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

libraryDependencies += "com.beachape.filemanagement" %% "schwatcher" % "0.0.1-SNAPSHOT"
```

Example Usage
-------------

```scala
import akka.actor.ActorSystem
import com.beachape.filemanagement.MonitorActor
import com.beachape.filemanagement.RegistryTypes._
import com.beachape.filemanagement.Messages._

import java.io.{FileWriter, BufferedWriter}

import java.nio.file.Paths
import java.nio.file.StandardWatchEventKinds._

implicit val system = ActorSystem("actorSystem")
val fileMonitorActor = system.actorOf(MonitorActor(concurrency = 2))

val modifyCallbackFile: Callback = { path => println(s"Something was modified in a file: $path")}
val modifyCallbackDirectory: Callback = { path => println(s"Something was modified in a directory: $path")}

val desktop = Paths get "/Users/lloyd/Desktop"
val desktopFile = Paths get "/Users/lloyd/Desktop/test"

/*
  This will receive callbacks for just the one file
 */
fileMonitorActor ! RegisterCallback(
  ENTRY_MODIFY,
  recursive = false,
  path = desktopFile,
  modifyCallbackFile)

/*
  If desktopFile is modified, this will also receive a callback
  it will receive callbacks for everything under the desktop directory
*/
fileMonitorActor ! RegisterCallback(
  ENTRY_MODIFY,
  recursive = false,
  path = desktop,
  modifyCallbackDirectory)


//modify a monitored file
val writer = new BufferedWriter(new FileWriter(desktopFile.toFile))
writer.write("Theres text in here wee!!")
writer.close

// #=> Something was modified in a file: /Users/a13075/Desktop/test.txt
//     Something was modified in a directory: /Users/a13075/Desktop/test.txt
```

## License

Copyright (c) 2013 by Lloyd Chan

Permission is hereby granted, free of charge, to any person obtaining a
copy of this software and associated documentation files (the
"Software"), to deal in the Software without restriction, including
without limitation the rights to use, copy, modify, merge, publish,
distribute, and to permit persons to whom the Software is furnished to do so, subject to
the following conditions:

The above copyright notice and this permission notice shall be included
in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS
OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.