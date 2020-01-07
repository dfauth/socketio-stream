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
import akka.http.scaladsl.server.RouteResult.Complete
import akka.stream._
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream.typed.scaladsl.{ActorSink, ActorSource}
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
  def blah(): Graph[FlowShape[Message, Message], NotUsed] = new GraphStage[FlowShape[Message, Message]] {
    val in: Inlet[Message] = Inlet("inlet")
    val out: Outlet[Message] = Outlet("outlet")
    override val shape: FlowShape[Message, Message] = FlowShape(in, out)
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
      // All state MUST be inside the GraphStageLogic,
      // never inside the enclosing GraphStage.
      // This state is safe to access and modify from all the
      // callbacks that are provided by GraphStageLogic and the
      // registered handlers.
      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          push(out, grab(in))
          logger.info(s"blah(): onPull")
          //          push(out, counter)
        }
      })
      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          pull(in)
          logger.info(s"blah(): onPush")
        }
      })
    }
  }

  def messageToEngineIoEnvelopeTransformer():Flow[Message, EngineIOEnvelope, NotUsed] = Flow.fromProcessor(() => new MessageToEngineIoEnvelopeProcessor(m => unwrap(m) match {
    case Success(s) => s
//    case Failure(t) => logger.error(t.getMessage, t)
  }))
  def engineIoEnvelopeToCommandTransformer(userCtx:UserContext[U]):Flow[EngineIOEnvelope, Command, NotUsed] = Flow.fromProcessor(() => new EngineIoEnvelopeToCommandProcessor(e => e.messageType.toActorMessage(userCtx, e)))

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
                  val id = userCtx.token
                  val ref:Future[ActorRef[Command]] = askActor{
                    supervisor ? FetchSession(id)
                  }.map {r => r.ref}
                  val fSink:Future[Sink[Command, NotUsed]] = ref.map[Sink[Command, NotUsed]] { r =>
                    ActorSink.actorRef[Command](r,
                      StreamComplete(id), // onCompleteMessage: T,
                      t => ErrorMessage(id, t) // onFailureMessage: Throwable => T
                    )
                  }
                  handleWebSocketMessages {
                  val sink:Sink[Message, NotUsed] = messageToEngineIoEnvelopeTransformer().via(engineIoEnvelopeToCommandTransformer(userCtx)).to(Sink.futureSink[Command, NotUsed](fSink))
                  val source = Source.empty
//                    val tmp:Message => Option[Message] = (m:Message) => unwrap(m) match {
//                      case Success(e) => handleEngineIOMessage(e).map(v => TextMessage.Strict(v.toString))
//                      case Failure(t) => {
//                        logger.error(t.getMessage, t)
//                        Some(TextMessage.Strict(EngineIOEnvelope.error(Some(t.getMessage)).toString))
//                      }
//                    }
//                    Flow.fromProcessor(() => new HandshakeProcessor[Message, Message](tmp))
                    Flow.fromSinkAndSource(sink, source)
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