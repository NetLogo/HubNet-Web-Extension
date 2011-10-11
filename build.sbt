scalaVersion := "2.9.1"

organization := "com.example"

name := "My Websocket Project"

version := "0.1.0"

libraryDependencies ++= Seq(
  "net.databinder" %% "unfiltered-netty-websockets" % "0.5.1",
  "net.liftweb" %% "lift-json" % "2.4-M4"
)
