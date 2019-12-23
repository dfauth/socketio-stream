package com.github.dfauth.socketio

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.github.dfauth.engineio.{Packets, Polling, Transport, Websocket, Envelope => EngineIOEnvelope}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

class SocketIoStream(system: ActorSystem) extends LazyLogging {

  val config = SocketIOConfig(ConfigFactory.load())
  val route = subscribe ~ static

  def authenticateToken(t: String): Boolean = true

  def octetStream(source: Source[ByteString, NotUsed]): ToResponseMarshallable = HttpResponse(entity = HttpEntity.Chunked.fromData(ContentTypes.`application/octet-stream`, source))

  def validateToken(token:String): Boolean = {
    logger.info(s"validatetoken($token) as true")
    true
  }

  def tokenAuth(r:String => Route):Route = optionalHeaderValueByName("x-auth") { token =>
    token.filter(t => validateToken(t)).map(t=>
      r(t)
    ).getOrElse {
      complete(HttpResponse(StatusCodes.Unauthorized))
    }
  }


  def subscribe = {
    path("socket.io" / ) { concat(

      get {
        tokenAuth { token =>
          val sid = token
          parameters('transport, 't, 'EIO) {
            (transport, requestId, eio) =>
              Transport.valueOf(transport) match {
                case Websocket => {
                  logger.info("WOOZ websocket")
                  //        try {
                  //          handleWebSocketMessages(
                  //            Flow.fromSinkAndSource(Sink.fromSubscriber(controller),
                  //              Source.fromPublisher(controller))
                  //              .keepAlive(5.seconds, () => TextMessage.Strict(new String(new PingMessage(ByteBuffer.wrap("ping".getBytes)).getPayload.array())))
                  //          )
                  //        } catch {
                  //          case t:Throwable => logger.error(t.getMessage, t)
                  //            throw t
                  //        }
                  complete("ok")
                }
                case activeTransport@Polling => {
                  logger.info(s"WOOZ polling ${requestId} ${eio} ${token}")
//                  val packets:Packets = Packets(EngineIOEnvelope.open(sid, config, activeTransport), EngineIOEnvelope.message(Envelope(Open)))
                  val packets:Packets = Packets(EngineIOEnvelope.open(sid, config, activeTransport))
                  complete(octetStream(Source.single(packets.toByteString)))
                }
              }
          }
        }
      },
      post {
        tokenAuth { token =>
          val sid = token
          parameters('transport, 't, 'EIO) {
            (transport, requestId, eio) =>
              Transport.valueOf(transport) match {
                case Websocket => {
                  logger.info("WOOZ websocket")
                  //        try {
                  //          handleWebSocketMessages(
                  //            Flow.fromSinkAndSource(Sink.fromSubscriber(controller),
                  //              Source.fromPublisher(controller))
                  //              .keepAlive(5.seconds, () => TextMessage.Strict(new String(new PingMessage(ByteBuffer.wrap("ping".getBytes)).getPayload.array())))
                  //          )
                  //        } catch {
                  //          case t:Throwable => logger.error(t.getMessage, t)
                  //            throw t
                  //        }
                  complete(HttpResponse(StatusCodes.OK))
                }
                case activeTransport@Polling => {
                  logger.info(s"WOOZ polling ${requestId} ${eio} ${token}")
                  complete(HttpResponse(StatusCodes.OK))
                }
              }
          }
        }
      })

    }
  }

  def static =
    path("") {
      getFromResource("static/index.html")
    } ~ pathPrefix("") {
      getFromResourceDirectory("static")
    }
}

object SocketIoStream {
  def apply(system: ActorSystem): SocketIoStream = new SocketIoStream(system)
}