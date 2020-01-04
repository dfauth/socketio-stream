package com.github.dfauth.socketio

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem => TypedActorSystem}
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Source}
import akka.util.{ByteString, Timeout}
import com.github.dfauth.actor._
import com.github.dfauth.engineio.EngineIOEnvelope._
import com.github.dfauth.engineio._
import com.github.dfauth.socketio.SocketIoStream.TokenValidator
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class SocketIoStream[U](system: ActorSystem, tokenValidator: TokenValidator[U]) extends LazyLogging {

  val config = SocketIOConfig(ConfigFactory.load())
  val route = subscribe ~ static

  val supervisor = TypedActorSystem[SupervisorMessage](Supervisor(), "socket_io")

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
          parameters('transport, 'EIO, 'sid.?) { // sid is optional in the initial GET
            (transport, eio, sid) =>
              EngineIOTransport.valueOf(transport) match {
                case Websocket => {
                  handleWebSocketMessages {
                    val tmp:Message => Option[Message] = (m:Message) => unwrap(m) match {
                      case Success(e) => handleEngineIOMessage(e).map(v => TextMessage.Strict(v.toString))
                      case Failure(t) => {
                        logger.error(t.getMessage, t)
                        Some(TextMessage.Strict(EngineIOEnvelope.error(Some(t.getMessage)).toString))
                      }
                    }
                    Flow.fromProcessor(() => new HandshakeProcessor[Message, Message](tmp))
                  }
                }
                case activeTransport@Polling => {
                  val e: EngineIOEnvelope = sid match {
                    case None => EngineIOEnvelope.open(userCtx.token, config, activeTransport)
                    case Some(v) => {
                      val ref:ActorRef[SupervisorMessage] = ActorUtils.asActor(Behaviors.setup[SupervisorMessage] { ctx => {
                        implicit val timeout: Timeout = 3.seconds
                        implicit val scheduler = supervisor.scheduler
                        ctx.ask[SupervisorMessage, SupervisorMessage](supervisor, ref => FetchSession(v, ref)) {
                          case Success(r) => {
                            logger.info(s"response: ${r}")
                            r
                          }
                          case Failure(t) => {
                            logger.error(t.getMessage, t)
                            ErrorMessage(t)
                          }
                        }
                        Behaviors.same
                      }
                      })
                    }
                      EngineIOEnvelope.connect("/chat")
                  }
                  complete(octetStream(Source.fromPublisher(DelayedClosePublisher(ByteString(EngineIOPackets(e).toBytes), config.longPollTimeout))))
                }
              }
          }
        }
      },
      post {
        tokenAuth { userCtx =>
          parameters('transport, 'EIO, 'sid) { // sid must exist
            (transport, eio, sid) =>
              entity(as[EngineIOEnvelope]) { e =>
                logger.info(s"entity: ${e} ${e.messageType} ${e.data}")

                // create session
                supervisor ! e.messageType.toActorMessage(userCtx, e)

                complete(HttpResponse(StatusCodes.OK, entity = HttpEntity("ok")))
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

trait UserContext[U] {
  val token:String
  val payload:U
}

object SocketIoStream {
  type TokenValidator[U] = String => Try[UserContext[U]]
  def apply[U](system: ActorSystem, tokenValidator: TokenValidator[U]): SocketIoStream[U] = new SocketIoStream(system, tokenValidator)
}
