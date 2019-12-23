package com.github.dfauth.engineio

import java.time.temporal.ChronoUnit

import akka.util.ByteString
import com.github.dfauth.engineio.Envelope.MessageType
import com.github.dfauth.socketio
import com.github.dfauth.socketio.SocketIOConfig
import spray.json.DefaultJsonProtocol

import scala.collection.mutable.ArrayBuffer

object Envelope {


  sealed class MessageType(value:Int) {
    def toBytes: Byte = value.toByte
  }

  case object Open extends MessageType(0)
  case object Close extends MessageType(1)
  case object Ping extends MessageType(2)
  case object Pong extends MessageType(3)
  case object Message extends MessageType(4)
  case object Upgrade extends MessageType(5)
  case object Noop extends MessageType(6)
  case object Error extends MessageType(7)

  def open(sid:String, config:SocketIOConfig, activeTransport:Transport):Envelope = Envelope(Open, Some(Packet(sid, config.transportsFiltering(activeTransport), config.pingInterval.get(ChronoUnit.SECONDS), config.pingTimeout.get(ChronoUnit.SECONDS))))
  def message(envelope: socketio.Envelope):Envelope = Envelope(Message, socketioPacket = Some(envelope))
}


case class Envelope(messageType:MessageType, data:Option[Packet] = None, socketioPacket:Option[socketio.Envelope] = None) {
  def toByteString: ByteString = {
    val payload:Array[Byte] = data.map(_.toBytes).getOrElse(Array.emptyByteArray)
    val bytes = ArrayBuffer[Byte]()
//    bytes.append(payload.length.toByte)
//    bytes.append(':'.toByte)
    bytes.append(messageType.toBytes)
    bytes.appendAll(payload)
    ByteString(bytes.toArray)
  }

}

case class Packet(sid:String,transports:Array[String],pingInterval:Long,pingTimeout:Long) {
  def toBytes: Array[Byte] = PacketJsonProtocol.packetFormat.write(this).toString().getBytes
}

case class Packets(packets:Envelope*) {
  def toByteString: ByteString = {
    packets.foldLeft[ByteString](ByteString())((acc, n) => acc ++ n.toByteString)

  }
}

object PacketJsonProtocol extends DefaultJsonProtocol {
  implicit val packetFormat = jsonFormat4(Packet)
}