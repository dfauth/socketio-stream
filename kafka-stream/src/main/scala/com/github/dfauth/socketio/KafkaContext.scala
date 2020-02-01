package com.github.dfauth.socketio

import akka.kafka.ConsumerMessage.{CommittableOffset, GroupTopicPartition}
import org.apache.avro.specific.SpecificRecordBase

object KafkaContext {
  def apply(groupId:String, topic:String, ackId:Long):(GroupTopicPartition, Long) = {
    val partitionId = (ackId % 100).toInt
    val offset = ackId / 100
    (new GroupTopicPartition(groupId, topic, partitionId), offset)
  }
}

case class KafkaContext[T <: SpecificRecordBase](topic:String, partition:Int, offset:Long, payload:T) {
  def ackId:Long = offset * 100 + partition
}

case class CommittableKafkaContext[T <: SpecificRecordBase](committableOffset:CommittableOffset, payload:T) {
  def ackId:Long = committableOffset.partitionOffset.offset * 100 + committableOffset.partitionOffset.key.partition
}
