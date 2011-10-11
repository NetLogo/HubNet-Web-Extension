import sbt._

class HubNetWebSockets(info: ProjectInfo) extends DefaultProject(info) {
  val ws = "net.databinder" %% "unfiltered-netty-websockets" % "0.5.1"
  val json = "net.liftweb" %% "lift-json" % "2.4-M4"
}
