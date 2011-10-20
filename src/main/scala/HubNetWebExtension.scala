import org.nlogo.api._
import org.nlogo.api.Syntax._

case class Server(webPort: Int, html:String, websocketPort:Int, hubnetPort: Int){
  @volatile private var alive = true
  def whileAliveDo(f: => Unit){
    new Thread(new Runnable { def run() { while(alive) f } }).start()
  }
  def stop(){ alive = false }

  def run() {
    spawn(websocketServer)
    spawn(webServer)
  }

  private def spawn(f: => Unit){ new Thread(new Runnable(){ def run(){ f }}).start() }

  private def websocketServer(): Unit = {
    import scala.collection.JavaConversions._
    import unfiltered.netty.websockets._
    import unfiltered.util._
    import scala.collection.mutable.ConcurrentMap
    import unfiltered.response.ResponseString
    case class Connection(ws:WebSocket){
      // when a js client connects, create a socket to hubnet.
      val jsonProtocol = new JSONProtocol(s => ws.send(s))
      val hubnetSocket = new java.net.Socket("127.0.0.1", hubnetPort)
      val hubnetProtocol = new HubNetProtocol(hubnetSocket.getInputStream, hubnetSocket.getOutputStream)

      // while alive, read messages from hubnet and send them to the websocket.
      whileAliveDo{
        try jsonProtocol.writeMessage(hubnetProtocol.readMessage())
        catch { case eof:java.io.EOFException => alive = false }
      }

      def receive(msg:String){
        // when we receive a message from a js client, we need to translate
        // it from json and send it to the hubnet server as a Message
        val hubnetMessageFromJSON = JSONProtocol.fromJSON(msg)
        hubnetProtocol.writeMessage(hubnetMessageFromJSON)
      }
      // when a client closes, we should disconnect from hubnet
      def exit(){
        alive = false
        hubnetProtocol.writeMessage(org.nlogo.hubnet.protocol.ExitMessage("client closed."))
      }
    }

    val connections: ConcurrentMap[Int, Connection] = new java.util.concurrent.ConcurrentHashMap[Int, Connection]
    def channelId(s:WebSocket) = s.channel.getId.intValue
    unfiltered.netty.Http(websocketPort).handler(unfiltered.netty.websockets.Planify({
      case _ => {
        case Open(s) => connections += (channelId(s) -> Connection(s))
        case Message(s, Text(msg)) => connections(channelId(s)).receive(msg)
        case Close(s) =>
          connections(channelId(s)).exit()
          connections -= channelId(s)
        case Error(s, e) => e.printStackTrace
      }
    })
    .onPass(_.sendUpstream(_)))
    .handler(unfiltered.netty.cycle.Planify{ case _ => ResponseString("not a websocket")})
    .run {s =>  }
  }

  private def webServer(): Unit = {
    import unfiltered.request._
    import unfiltered.response._
    unfiltered.netty.Http(webPort).plan(unfiltered.netty.cycle.Planify {
      case _ => ResponseString(html)
    }).run()
  }
}

object HubNetWebExtension {
  var so: Option[Server] = None
  var em: org.nlogo.workspace.ExtensionManager = null
  def start(webPort: Int, htmlPath:String, webSocketPort:Int){
    so match {
      case Some(s) => throw new ExtensionException("already listening")
      case None => {
        so = Some(Server(
          webPort,
          scala.io.Source.fromFile(em.workspace.getModelDir + java.io.File.separator + htmlPath).getLines.mkString("\n"),
          webSocketPort, hubNetPort)); so.foreach(_.run()) }
    }
  }
  def stop(){ so.foreach(_.stop); so = None }
  def hubNetPort =
    em.workspace.getHubNetManager.asInstanceOf[org.nlogo.hubnet.server.HubNetManager].connectionManager.port
}

class HubNetWebExtension extends DefaultClassManager {
  def load(manager: PrimitiveManager) {
    manager.addPrimitive("start", new Start)
    manager.addPrimitive("stop", new Stop)
  }
  override def runOnce(em: ExtensionManager) {
    HubNetWebExtension.em = em.asInstanceOf[org.nlogo.workspace.ExtensionManager]
  }
  override def unload(em: ExtensionManager){ HubNetWebExtension.stop() }
}

class Start extends DefaultCommand {
  override def getSyntax = commandSyntax(Array(NumberType, StringType, NumberType))
  def perform(args: Array[Argument], context: Context){
    HubNetWebExtension.start(args(0).getIntValue, args(1).getString, args(2).getIntValue)
  }
}

class Stop extends DefaultCommand {
  override def getSyntax = commandSyntax(Array[Int]())
  def perform(args: Array[Argument], context: Context){ HubNetWebExtension.stop() }
}