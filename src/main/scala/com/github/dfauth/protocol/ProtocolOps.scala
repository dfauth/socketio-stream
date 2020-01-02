package com.github.dfauth.protocol

import com.github.dfauth.engineio.EngineIOPacket

import scala.collection.mutable.ArrayBuffer

trait ProtocolMessageType {
  val value:Int
  def payload(b:Array[Byte]):Option[EngineIOPacket] = None
  def toByte: Byte = (value + 48).toByte
}

trait Bytable {
  def toBytes:Array[Byte]
}

trait ProtocolOps extends Bytable {

  val messageType:ProtocolMessageType
  val data:Option[Bytable]

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

  def toBytes: Array[Byte] = {
    val payload:Array[Byte] = data.map(_.toBytes).getOrElse(Array.emptyByteArray)
    val bytes = ArrayBuffer[Byte]()
    bytes.appendAll(intToBytes(payload.length+1))
    bytes.append(0xff.toByte)
    bytes.append(messageType.toByte)
    bytes.appendAll(payload)
    bytes.toArray
  }

  override def toString: String = {
    val payload:String = data.map(_.toString).getOrElse(new String())
    val buffer = new StringBuffer()
    buffer.append(messageType.value)
    buffer.append(payload)
    buffer.toString
  }

}
