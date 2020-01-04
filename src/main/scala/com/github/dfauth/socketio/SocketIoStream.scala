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

  def octetStream(source: Source[ByteString, NotUsed]): ToResponseMarshallable = HttpResponse(entity = HttpEntity.Chunked.fromData(ContentTypes.`application/octet-stream`, source))

  def tokenAuth(r:UserContext[U] => Route):Route = optionalHeaderValueByName("x-auth") { token =>
    val handleValidation = (t:String) => tokenValidator(t) match {
      case Success(u) => {
        logger.info(s"validatetoken($t): ${u}")
        r(u)
      }
      case Failure(e) => {
        logger.error(e.getMessage, e)
        complete(HttpResponse(StatusCodes.Unauthorized))
      }
    }
    token.map(t=>
      handleValidation(t)
    ).getOrElse {
      // try a query parameter
      parameters('sid) { t =>
        handleValidation(t)
      }
    }
  }


  def subscribe = {
    path("socket.io" / ) { concat(

      get {
        tokenAuth { userCtx =>
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
                  val packets:EngineIOPackets = sid.map { _ => EngineIOPackets(EngineIOEnvelope.connect("/chat")) }.getOrElse { EngineIOPackets(EngineIOEnvelope.open(userCtx.token, config, activeTransport))}
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

trait UserContext[U] {
  val token:String
  val payload:U
}

object SocketIoStream {
  type TokenValidator[U] = String => Try[UserContext[U]]
  def apply[U](system: ActorSystem, tokenValidator: TokenValidator[U]): SocketIoStream[U] = new SocketIoStream(system, tokenValidator)
}
