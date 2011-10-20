import java.io._
import org.nlogo.hubnet.protocol._
import net.liftweb.json._
import net.liftweb.json.JsonDSL._

case class JSONProtocol(write: String => Unit) {
  def writeMessage(m:AnyRef) { write(JSONProtocol.toJSON(m)) }
}

case class HubNetProtocol(in:InputStream, out:OutputStream) {
  val oIn = new ObjectInputStream(in)
  val oOut = new ObjectOutputStream(out)
  def readMessage(): AnyRef = oIn.readObject
  def writeMessage(m:AnyRef) { oOut.writeObject(m) }
}

object JSONProtocol {

  /**
   val message = {
     "type": "ActivityCommand",
     "fields": {
        "tag": "my-slider",
        "content": {
          "type": "Number"
          "value": 50
        }
      }
   };
   */
  def fromJSON(in:String): AnyRef = {
    val message = parse(in)
    def getString(v:JValue) = v.values.toString
    val mType = getString(message \ "type")
    val fields = (message \ "fields")
    def fieldString(name:String) = getString(fields \ name)
    def typedValue(v:JValue): AnyRef = {
      val contentType = getString(v \ "type")
      val contentValue = (v \ "value").values
      contentType match {
        case "Boolean" => contentValue.asInstanceOf[Boolean].asInstanceOf[AnyRef]
        // for some reason lift-json gives back a BigInt here instead of Int.
        case "Integer" => contentValue.asInstanceOf[BigInt].toInt.asInstanceOf[AnyRef]
        case "Double" => contentValue.asInstanceOf[BigInt].toDouble.asInstanceOf[AnyRef]
        case "String" => contentValue.asInstanceOf[String]
      }
    }

    mType match {
      case "HandshakeFromServer" => HandshakeFromServer(fieldString("activityName"), Nil)
      case "HandshakeFromClient" => HandshakeFromClient(fieldString("userId"), fieldString("clientType"))
      case "LoginFailure" => LoginFailure(fieldString("content"))
      case "ExitMessage" => ExitMessage(fieldString("reason"))
      case "EnterMessage" => EnterMessage
      case "ActivityCommand" => ActivityCommand(fieldString("tag"), typedValue(fields \ "content"))
      // this special case gets turned into a String
      case "VersionMessage" => getString(fieldString("version"))

      // TODO
      //  case "Text" => sys.error("implement me")
      //  case "WidgetControl" => WidgetControl(?, field("tag") )
      //  case "DisableView" => DisableView
      //  case "ViewUpdate" => sys.error("implement me")
      //  case "PlotControl" => sys.error("implement me")
      //  case "PlotUpdate" => sys.error("implement me")
      //  case "OverrideMessage" => sys.error("implement me")
      //  case "ClearOverrideMessage" => ClearOverrideMessage
      //  case "AgentPerspectiveMessage" => sys.error("implement me")
      case s => sys.error("implement me: " + s)
    }
  }

  def toJSON(hubNetMessage:AnyRef): String = {

    def message[A <% JValue](mType:String, fields: A): JValue = ("type", mType) ~ ("fields", fields)
    def emptyMessage(mType:String): JValue = ("type", mType)
    def typed(value: AnyRef): JValue = {
      val v =
        if(value.isInstanceOf[Int]) JInt(value.asInstanceOf[Int])
        else if(value.isInstanceOf[Boolean]) JBool(value.asInstanceOf[Boolean])
        else if(value.isInstanceOf[Double]) JDouble(value.asInstanceOf[Double])
        else JString(value.toString)
      ("type", value.getClass.getSimpleName) ~ ("value", v)
    }
    val json = hubNetMessage match {
      case HandshakeFromServer(activityName, interface) =>
        // TODO: handle client interface
        message("HandshakeFromServer", ("activityName", activityName))
      case HandshakeFromClient(userId, clientType) =>
        message("HandshakeFromClient", ("userId", userId) ~ ("clientType", clientType))
      case LoginFailure(content) =>
        message("LoginFailure", ("content", content))
      case ExitMessage(reason) =>
        message("ExitMessage", ("reason", reason))
      case WidgetControl(content, tag) =>
        message("WidgetControl", ("tag", tag) ~ ("content", typed(content)))
      case DisableView =>
        emptyMessage("DisableView")
      case EnterMessage =>
        emptyMessage("EnterMessage")
      case ActivityCommand(tag, content) =>
        message("ActivityCommand", ("tag", tag) ~ ("content", typed(content)))
      case ClearOverrideMessage =>
        emptyMessage("ClearOverrideMessage")
      case Text(content, messageType) =>
        message("Text", ("content", content) ~ ("messageType", messageType.toString))
      case s:String => message("VersionMessage", ("version", s))

      // TODO:
      case ViewUpdate(worldData) =>
        emptyMessage("ViewUpdate")
      case PlotControl(content, plotName) =>
        emptyMessage("PlotControl")
      case PlotUpdate(plot) =>
        emptyMessage("PlotUpdate")
      case OverrideMessage(data, clear) =>
        emptyMessage("OverrideMessage")
      case AgentPerspectiveMessage(bytes) =>
        emptyMessage("AgentPerspectiveMessage")
    }
    compact(render(json))
  }
}