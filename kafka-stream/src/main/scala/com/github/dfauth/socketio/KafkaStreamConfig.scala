package com.github.dfauth.socketio

import com.typesafe.config.Config

case class KafkaStreamConfig(config: Config) {
    def getContextConfig(ctx:String):Config = config.getConfig(ctx)
}
