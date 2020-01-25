package com.github.dfauth.socketio.kafka

import java.nio.ByteBuffer
import java.util.Optional

import com.typesafe.scalalogging.LazyLogging
import kafka.coordinator.group.{BaseKey, GroupMetadataManager, OffsetKey}
import org.apache.kafka.common.serialization.Deserializer

case class OffsetKey(override val version:Short, override val key:Any) extends kafka.coordinator.group.BaseKey

case class OffsetAndMetadata(offset: Long,
                             leaderEpoch: Optional[Integer],
                             metadata: String,
                             commitTimestamp: Long,
                             expireTimestamp: Option[Long])

class OffsetKeyDeserializer extends Deserializer[OffsetKey] with LazyLogging {
  override def deserialize(topic: String, data: Array[Byte]): OffsetKey = {
    val k = GroupMetadataManager.readMessageKey(ByteBuffer.wrap(data))
    OffsetKey(k.version, k.key)
  }
}

class OffsetValueDeserializer(groupId:String) extends Deserializer[OffsetAndMetadata] with LazyLogging {
  override def deserialize(topic: String, data: Array[Byte]): OffsetAndMetadata = {
    val v = GroupMetadataManager.readOffsetMessageValue(ByteBuffer.wrap(data))
    OffsetAndMetadata(v.offset, v.leaderEpoch, v.metadata, v.commitTimestamp, v.expireTimestamp)
  }
}
