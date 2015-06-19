name := "WebCrawler"

version := "1.0"

scalaVersion := "2.11.6"

libraryDependencies += "com.typesafe.akka" %% "akka-actor" % "2.3.10"
libraryDependencies += "org.apache.commons" % "commons-lang3" % "3.3"

resolvers += "Akka Snapshots" at "http://repo.akka.io/snapshots/"
    