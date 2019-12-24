package com.github.dfauth.socketio

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.github.dfauth.engineio.{EngineIOEnvelope, EngineIOPackets, EngineIOTransport, Polling, Websocket}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration.FiniteDuration

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
      // try a query parameter
      parameters('sid) { token =>
        if(validateToken(token)) {
          r(token)
        } else {
          complete(HttpResponse(StatusCodes.Unauthorized))
        }
      }
    }
  }


//  def probe: BinaryMessage.Strict => BinaryMessage.Strict = (a:BinaryMessage.Strict) => {
//    val src = a.data.map(b => )
//  }

  def probe: Message => Message = a => {
    a match {
      case TextMessage.Strict(b) => logger.info(s"bytes: ${b}")
      case x => logger.info(s"x: ${x}")
    }
    TextMessage.Strict(EngineIOPackets(EngineIOEnvelope.heartbeat(Some("probe"))).toString)
  }

  def subscribe = {
    path("socket.io" / ) { concat(

      get {
        tokenAuth { token =>
          val sid = token
          parameters('transport, 'EIO) {
            (transport, eio) =>
              EngineIOTransport.valueOf(transport) match {
                case Websocket => {
                  handleWebSocketMessages(
                    Flow.fromFunction(probe)
                      //.keepAlive(FiniteDuration(config.pingInterval.get(ChronoUnit.SECONDS),TimeUnit.SECONDS), () => BinaryMessage.Strict(EngineIOPackets(EngineIOEnvelope.heartbeat()).toByteString))
                  )
                }
                case activeTransport@Polling => {
                  val packets:EngineIOPackets = EngineIOPackets(EngineIOEnvelope.open(sid, config, activeTransport))
                  complete(octetStream(Source.fromPublisher(DelayedClosePublisher(packets.toByteString, 2000))))
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
              EngineIOTransport.valueOf(transport) match {
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