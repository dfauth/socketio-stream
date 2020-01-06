package com.github.dfauth.socketio

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem => TypedActorSystem}
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Flow, Source}
import akka.util.{ByteString, Timeout}
import com.github.dfauth.actor.ActorUtils._
import com.github.dfauth.actor._
import com.github.dfauth.engineio.EngineIOEnvelope._
import com.github.dfauth.engineio._
import com.github.dfauth.socketio.SocketIoStream.TokenValidator
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class SocketIoStream[U](system: ActorSystem, tokenValidator: TokenValidator[U]) extends LazyLogging {

  val config = SocketIOConfig(ConfigFactory.load())
  val route = subscribe

  val typedSystem = TypedActorSystem[Command](Supervisor(), "socket_io")
  val supervisor:ActorRef[Command] = typedSystem

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
              implicit val timeout: Timeout = 3.seconds
              implicit val scheduler = typedSystem.scheduler
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
                  val f:Future[EngineIOEnvelope] = sid match {
                    case None => {
                      askActor {
                        supervisor ? CreateSession(userCtx)
                      }.map {r => EngineIOEnvelope.open(userCtx.token, config, activeTransport) }
                    }
                    case Some(v) => {
                      askActor{
                        supervisor ? FetchSession(v)
                      }.map {r => EngineIOEnvelope.connect(r.namespace)}
                    }
                  }
                  complete(octetStream(Source.fromPublisher(DelayedClosePublisher(f.map {v => ByteString(EngineIOPackets(v).toBytes)}, config.longPollTimeout))))
                }
              }
          }
        }
      },
      post {
        tokenAuth { userCtx =>
          parameters('transport, 'EIO, 'sid, 't.?) { // sid must exist
            (transport, eio, sid, t) =>
              entity(as[EngineIOEnvelope]) { e =>
                logger.info(s"entity: ${e} ${e.messageType} ${e.data} t: ${t}")

                // add namespace
                supervisor ! e.messageType.toActorMessage(userCtx, e)

                complete(HttpResponse(StatusCodes.OK, entity = HttpEntity("ok")))
              }
          }
        }
      })

    }
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
