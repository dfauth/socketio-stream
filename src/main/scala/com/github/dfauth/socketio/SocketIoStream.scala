package com.github.dfauth.socketio

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import com.github.dfauth.engineio.EngineIOEnvelope._
import com.github.dfauth.engineio._
import com.github.dfauth.socketio.SocketIoStream.TokenValidator
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

class SocketIoStream[U](system: ActorSystem, tokenValidator: TokenValidator[U]) extends LazyLogging {

  val config = SocketIOConfig(ConfigFactory.load())
  val route = subscribe ~ static

  def authenticateToken(t: String): Boolean = true

  def octetStream(source: Source[ByteString, NotUsed]): ToResponseMarshallable = HttpResponse(entity = HttpEntity.Chunked.fromData(ContentTypes.`application/octet-stream`, source))

  def validateToken(token:String): Boolean = {
    tokenValidator(token) match {
      case Success(u) => {
        logger.info(s"validatetoken($token): ${u}")
        true
      }
      case Failure(t) => {
        logger.error(t.getMessage, t)
        false
      }
    }
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


  def subscribe = {
    path("socket.io" / ) { concat(

      get {
        tokenAuth { token =>
          parameters('transport, 'EIO, 'sid.?) {
            (transport, eio, sid) =>
              EngineIOTransport.valueOf(transport) match {
                case Websocket => {
                  handleWebSocketMessages({
                    val tmp:Message => Future[Option[Message]] = (m:Message) => unwrap(m)
                      .map { e => {
                        probe(e).map(v => TextMessage.Strict(v.toString))
                      }
//                      .failed.map { t => {
//                          logger.error(t.getMessage, t)
//                          TextMessage.Strict(EngineIOEnvelope.error(Some(t.getMessage)).toString)
//                        }
                    }
                    Flow.fromProcessor(() => new HandshakeProcessor[Message, Message](tmp))
                  }
                  )
                }
                case activeTransport@Polling => {
                  val packets:EngineIOPackets = sid.map { _ => EngineIOPackets(EngineIOEnvelope.connect("/chat")) }.getOrElse { EngineIOPackets(EngineIOEnvelope.open(token, config, activeTransport))}
                  complete(octetStream(Source.fromPublisher(DelayedClosePublisher(ByteString(packets.toBytes), 2000))))
                }
              }
          }
        }
      },
      post {
        tokenAuth { token =>
          entity(as[EngineIOEnvelope]) { e =>
            logger.info(s"entity: ${e} ${e.messageType} ${e.data}")
            complete(HttpResponse(StatusCodes.OK, entity = HttpEntity("ok")))
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
  type TokenValidator[U] = String => Try[U]
  def apply[U](system: ActorSystem, tokenValidator: TokenValidator[U]): SocketIoStream[U] = new SocketIoStream(system, tokenValidator)
}
