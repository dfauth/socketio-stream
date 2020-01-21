package com.github.dfauth.socketio.protocol

import java.nio.charset.Charset
import java.time.temporal.ChronoUnit

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.unmarshalling.{FromRequestUnmarshaller, Unmarshaller}
import akka.stream.Materializer
import com.github.dfauth.socketio.actor.{Command, EndSession, ErrorMessage, StreamComplete}
import com.github.dfauth.socketio.{SocketIOConfig, UserContext}
import com.typesafe.scalalogging.LazyLogging
import spray.json.DefaultJsonProtocol

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

object EngineIOEnvelope extends LazyLogging {

  def fromBytes(b: Array[Byte]):Try[EngineIOEnvelope] = {
    b match {
      case Array() => {
        val e = new RuntimeException("Unexpected empty byte array")
        logger.error(e.getMessage, e)
        Failure(e)
      }
      case Array(x) => {
        val msgType = EngineIOMessageType.fromByte(x)
        Success(EngineIOEnvelope(msgType))
      }
      case Array(x, _*) => {
        val msgType = EngineIOMessageType.fromByte(x)
        val payload = msgType.payload(b.slice(1, b.length))
        Success(EngineIOEnvelope(msgType, payload))
      }
      case x => {
        val e = new RuntimeException(s"Unmatcheable byte array ${x}")
        logger.error(e.getMessage, e)
        Failure(e)
      }
    }
  }

  def fromString(str: String):Try[EngineIOEnvelope] = {
    str.split(":") match {
      case Array() => {
        val t = new IllegalArgumentException(s"Unexpected empty string")
        logger.error(t.getMessage, t)
        Failure(t)
      }
      case Array(lenString, payload) => {
        val len = lenString.toString.toInt
        payload.toCharArray match {
          case Array(msgType, _*) => Success(EngineIOEnvelope(EngineIOMessageType.fromChar(msgType),
            SocketIOEnvelope.fromString(str.substring(str.length-len.toString.toInt+1, str.length)).toOption
          ))

        }
      }
      case _ => {
        val t = new IllegalArgumentException(s"Unexpected string: ${str}")
        logger.error(t.getMessage, t)
        Failure(t)
      }
    }
  }

  val UTF8 = Charset.forName("UTF-8")

  def open(sid:String, config:SocketIOConfig, activeTransport:EngineIOTransport):EngineIOEnvelope = EngineIOEnvelope(Open, Some(EngineIOSessionInitPacket(sid, config.transportsFiltering(activeTransport), config.pingInterval.get(ChronoUnit.SECONDS)*1000, config.pingTimeout.get(ChronoUnit.SECONDS)*1000)))
  def connect(message:String):EngineIOEnvelope = EngineIOEnvelope(Msg, Some(SocketIOEnvelope.connect(message)))
  def connect():EngineIOEnvelope = EngineIOEnvelope(Msg, Some(SocketIOEnvelope.connect()))
  def heartbeat(optMessage:Option[String] = None):EngineIOEnvelope = optMessage.map(m => EngineIOEnvelope(Pong, Some(EngineIOStringPacket(m)))).getOrElse(EngineIOEnvelope(Pong))
  def error(optMessage:Option[String] = None):EngineIOEnvelope = optMessage.map(m => EngineIOEnvelope(EngineIOError, Some(EngineIOStringPacket(m)))).getOrElse(EngineIOEnvelope(EngineIOError))

  implicit val decoder:FromRequestUnmarshaller[EngineIOEnvelope] = new Unmarshaller[HttpRequest, EngineIOEnvelope](){
    override def apply(req: HttpRequest)(implicit ec: ExecutionContext, materializer: Materializer): Future[EngineIOEnvelope] = {
      val p = Promise[EngineIOEnvelope]()
      Future({
        val str = req.entity.dataBytes.runFold(new String)((acc, byteString)=> acc ++ byteString.utf8String)
        str.onComplete({
          case Success(s) => fromString(s).map { p.success(_)} getOrElse {p.failure(new RuntimeException("Failed to collect bytes"))}
          case Failure(t) => {
            logger.error(t.getMessage, t)
            p.failure(t)
          }
        })(global)
      })(global)
      p.future
    }
  }

  def unwrap: Message => Try[EngineIOEnvelope] = m => m match {
    case TextMessage.Strict(b) => EngineIOEnvelope.fromBytes(b.getBytes)
    case x => Failure(new IllegalArgumentException(s"Unexpected message: ${x}"))
  }

  def handleEngineIOMessage: EngineIOEnvelope => Option[EngineIOEnvelope] = {
    case EngineIOEnvelope(Upgrade, None) => None // ignore
    case EngineIOEnvelope(Msg, Some(EngineIOSocketIOPacket(m))) => {
      logger.info(s"received message: ${m}")
      None
    } // ignore
    case EngineIOEnvelope(Ping, None) => Some(EngineIOEnvelope.heartbeat())
    case EngineIOEnvelope(Ping, Some(EngineIOStringPacket(m))) => Some(EngineIOEnvelope.heartbeat(Some(m)))
  }

  def handleEngineIOHeartbeat: PartialFunction[EngineIOEnvelope, EngineIOEnvelope] = {
    case EngineIOEnvelope(Ping, None) => {
      logger.info(s"handleEngineIOHeartbeat received Ping")
      EngineIOEnvelope.heartbeat()
    }
    case EngineIOEnvelope(Ping, Some(EngineIOStringPacket(m))) => {
      logger.info(s"handleEngineIOHeartbeat received Ping${m}")
      EngineIOEnvelope.heartbeat(Some(m))
    }
  }

  def handleEngineIOMessages: EngineIOEnvelope => Boolean = {
    case EngineIOEnvelope(Msg, _) => true
    case _ => false
  }

}

object EngineIOMessageType {

  def fromChar(c:Char) = fromByte(c.toByte)

  def fromByte(b: Byte):EngineIOMessageType = (b.toInt - 48 )  match {
    case 0 => Open
    case 1 => Close
    case 2 => Ping
    case 3 => Pong
    case 4 => Msg
    case 5 => Upgrade
    case 6 => Noop
    case 7 => EngineIOError
  }

}

sealed class EngineIOMessageType(override val value:Int) extends ProtocolMessageType with LazyLogging {
  def toActorMessage[U](ctx:UserContext[U], e: EngineIOEnvelope): Command = ???
}

case object Open extends EngineIOMessageType(0)
case object Close extends EngineIOMessageType(1) {
  override def toActorMessage[U](ctx:UserContext[U], e: EngineIOEnvelope): Command = {
    e.data match {
      case None => EndSession(ctx.token)
    }
  }
}
case object Ping extends EngineIOMessageType(2) {
  override def payload(b:Array[Byte]):Option[EngineIOPacket] = Some(EngineIOStringPacket((b.map(_.toChar)).mkString))
}
case object Pong extends EngineIOMessageType(3) {
}
case object Msg extends EngineIOMessageType(4) {
  override def payload(b:Array[Byte]):Option[EngineIOPacket] = {
    SocketIOEnvelope.fromBytes(b) match {
      case Success(s) => Some(EngineIOSocketIOPacket(s))
      case Failure(t) => {
        logger.error(t.getMessage, t)
        None // TODO return Try[Option[EngineIOPacket]]
      }
    }
  }
  override def toActorMessage[U](ctx:UserContext[U], e: EngineIOEnvelope): Command = {
    e.data match {
      case Some(EngineIOSocketIOPacket(SocketIOEnvelope(msgType, data))) => {
        logger.info(s"SocketIOEnvelope contains: ${msgType} ${data}")
        msgType.toActorMessage(ctx, data)
      }
      case Some(SocketIOEnvelope(msgType, data)) => {
        logger.info(s"SocketIOEnvelope contains: ${msgType} ${data}")
        msgType.toActorMessage(ctx, data)
      }
      case x => {
        val e = new RuntimeException(s"Unexpected message type: ${x}")
        logger.error(e.getMessage, e)
        ErrorMessage(ctx.token, e)
      }
    }
  }
}
case object Upgrade extends EngineIOMessageType(5)
case object Noop extends EngineIOMessageType(6)
case object EngineIOError extends EngineIOMessageType(7)

case class EngineIOEnvelope(messageType:EngineIOMessageType, data:Option[Bytable] = None) extends ProtocolOps

trait EngineIOPacket extends Bytable {
  def toBytes():Array[Byte]
}

case class EngineIOSessionInitPacket(sid:String, upgrades:Array[String], pingInterval:Long, pingTimeout:Long) extends EngineIOPacket {
  def toBytes: Array[Byte] = EngineIO.packetFormat.write(this).toString().getBytes(EngineIOEnvelope.UTF8)
}

case class EngineIOEmptyPacket() extends EngineIOPacket {
  def toBytes: Array[Byte] = Array.emptyByteArray
  override def toString:String = new String
}

case class EngineIOStringPacket(message:String) extends EngineIOPacket {
  def toBytes: Array[Byte] = message.getBytes(EngineIOEnvelope.UTF8)
  override def toString:String = message
}

case class EngineIOSocketIOPacket(socketIo:SocketIOEnvelope) extends EngineIOPacket {
  def toBytes: Array[Byte] = socketIo.toBytes
  override def toString:String = socketIo.toString
}

case class EngineIOPackets(packets:EngineIOEnvelope*) {
  def toBytes: Array[Byte] = {
    packets.foldLeft[Array[Byte]](Array.emptyByteArray)((acc, n) => acc ++ n.toBytes)
  }
  override def toString:String = {
    packets.foldLeft[String](new String())((acc, n) => acc ++ n.toString)
  }
}

object EngineIO extends DefaultJsonProtocol {
  implicit val packetFormat = jsonFormat4(EngineIOSessionInitPacket)
}