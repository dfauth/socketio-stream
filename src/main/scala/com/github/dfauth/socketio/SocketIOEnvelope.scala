package com.github.dfauth.socketio

import akka.util.ByteString
import com.github.dfauth.engineio.{EngineIOEnvelope, EngineIOPacket}

import scala.collection.mutable.ArrayBuffer

sealed class MessageType(value:Int) {
  def getValue = value
  def toByte: Byte = (value + 48).toByte
}

case object Connect extends MessageType(0)
case object Disconnect extends MessageType(1)
case object Event extends MessageType(2)
case object Ack extends MessageType(3)
case object Error extends MessageType(4)
case object BinaryEvent extends MessageType(5)
case object BinaryAck extends MessageType(6)

object SocketIOEnvelope {
  def connect(namespace:String) = {
    SocketIOEnvelope(Connect, Some(SocketIOPacket(namespace)))
  }
}

case class SocketIOEnvelope(messageType:MessageType, data:Option[SocketIOPacket] = None) {

  def convert(i:Int, size:Int): Array[Byte] = {
    size match {
      case 1 => {
        Array((i % 10).toByte)
      }
      case x => {
        val pow1 = Math.pow(10, size).toInt
        val v = i % pow1
        val pow = Math.pow(10, size-1).toInt
        val u = v / pow
        val result = convert(i, size - 1)
        Array(u.toByte) ++ result
      }
    }
  }

  def intToBytes(i:Int): Array[Byte] = {
    val size = Math.log10(i).toInt
    Array(0.toByte) ++ convert(i, size+1)
  }

  def toByteString: ByteString = {
    val payload:Array[Byte] = data.map(_.toBytes).getOrElse(Array.emptyByteArray)
    val bytes = ArrayBuffer[Byte]()
    bytes.appendAll(intToBytes(payload.length+1))
    bytes.append(0xff.toByte)
    bytes.append(messageType.toByte)
    bytes.appendAll(payload)
    ByteString(bytes.toArray)
  }

  override def toString: String = {
    val payload:String = data.map(_.toString).getOrElse(new String())
    val buffer = new StringBuffer()
    buffer.append(messageType.getValue)
    buffer.append(payload)
    buffer.toString
  }

}

case class SocketIOPacket(namespace:String) {
  def toBytes: Array[Byte] = namespace.getBytes(EngineIOEnvelope.UTF8)
  override def toString:String = namespace
}
