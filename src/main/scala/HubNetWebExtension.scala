import java.net.Socket
import org.nlogo.api._
import org.nlogo.api.Syntax._
import org.nlogo.hubnet.protocol.ExitMessage
import org.nlogo.hubnet.server.HubNetManager

case class Server(serverPort:Int, hubnetPort: Int){
  import unfiltered.netty.websockets._
  import unfiltered.util._
  import scala.collection.mutable.ConcurrentMap
  import unfiltered.response.ResponseString
  import scala.collection.JavaConversions._

  @volatile private var alive = true
  def whileAliveDo(f: => Unit){
    new Thread(new Runnable { def run() { while(alive) f } }).start()
  }
  def stop(){ alive = false }

  case class Connection(ws:WebSocket){
    // when a js client connects, create a socket to hubnet.
    val jsonProtocol = new JSONProtocol(s => ws.send(s))
    val hubnetSocket: Socket = new Socket("127.0.0.1", hubnetPort)
    val hubnetProtocol = new HubNetProtocol(hubnetSocket.getInputStream, hubnetSocket.getOutputStream)

    // while alive, read messages from hubnet and send them to the websocket.
    whileAliveDo{ jsonProtocol.writeMessage(hubnetProtocol.readMessage())  }

    def receive(msg:String){
      // when we receive a message from a js client, we need to translate
      // it from json and send it to the hubnet server as a Message
      val hubnetMessageFromJSON = JSONProtocol.fromJSON(msg)
      println("hubnetMessageFromJSON: " + hubnetMessageFromJSON)
      hubnetProtocol.writeMessage(hubnetMessageFromJSON)
    }
    // when a client closes, we should disconnect from hubnet
    def exit(){ hubnetProtocol.writeMessage(ExitMessage("client closed."))}
  }

  def run() {
    println("running new server on: " + serverPort)
    val connections: ConcurrentMap[Int, Connection] = new java.util.concurrent.ConcurrentHashMap[Int, Connection]
    def channelId(s:WebSocket) = s.channel.getId.intValue

    new Thread(new Runnable() {
      def run() {
        unfiltered.netty.Http(serverPort).handler(unfiltered.netty.websockets.Planify({
          case _ => {
            case Open(s) =>
              println("connection opened: " + s)
              connections += (channelId(s) -> Connection(s))
            case Message(s, Text(msg)) =>
              println("got message: " + msg)
              connections(channelId(s)).receive(msg)
            case Close(s) =>
              println("connection closed: " + s)
              connections(channelId(s)).exit()
              connections -= channelId(s)
            case Error(s, e) => e.printStackTrace
          }
        })
        .onPass(_.sendUpstream(_)))
        .handler(unfiltered.netty.cycle.Planify{ case _ => ResponseString("not a websocket")})
        .run {s =>  } //Browser.open("file://goo.html")
      }
    }).start()
  }
}

object HubNetWebExtension {
  var so: Option[Server] = None
  var em: org.nlogo.workspace.ExtensionManager = null
  def start(port:Int){
    so match {
      case Some(s) => throw new ExtensionException("already listening")
      case None => { so = Some(Server(port, hubNetPort)); so.foreach(_.run()) }
    }
  }
  def stop(){ so.foreach(_.stop); so = None }
  def hubNetPort =
    em.workspace.getHubNetManager.asInstanceOf[HubNetManager].connectionManager.port
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
  override def getSyntax = commandSyntax(Array(NumberType))
  def perform(args: Array[Argument], context: Context){
    HubNetWebExtension.start(args(0).getIntValue)
  }
}

class Stop extends DefaultCommand {
  override def getSyntax = commandSyntax(Array[Int]())
  def perform(args: Array[Argument], context: Context){ HubNetWebExtension.stop() }
}
