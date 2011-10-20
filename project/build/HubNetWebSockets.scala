import sbt._

class HubNetWebSockets(info: ProjectInfo) extends DefaultProject(info) {
  val ws = "net.databinder" %% "unfiltered-netty-websockets" % "0.5.1"
  val unfilteredFilter = "net.databinder" %% "unfiltered-filter" % "0.5.0"
  val jetty = "net.databinder" %% "unfiltered-jetty" % "0.5.0"
  val netty = "net.databinder" %% "unfiltered-netty" % "0.5.0"
  val nettyServer= "net.databinder" %% "unfiltered-netty-server" % "0.5.0"
  val avsl = "org.clapper" %% "avsl" % "0.3.6"
  val unfilteredSpec = "net.databinder" %% "unfiltered-spec" % "0.5.0" % "test"
  val json = "net.liftweb" %% "lift-json" % "2.4-M4"
}
