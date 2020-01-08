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
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.typed.scaladsl.ActorSink
import akka.util.{ByteString, Timeout}
import com.github.dfauth.actor.ActorUtils._
import com.github.dfauth.actor._
import com.github.dfauth.socketio.Processors._
import com.github.dfauth.engineio.EngineIOEnvelope._
import com.github.dfauth.engineio.{EngineIOEnvelope, Upgrade, _}
import com.github.dfauth.socketio.SocketIoStream.TokenValidator
import com.github.dfauth.utils.ShortCircuit
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.Processor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class SocketIoStream[U](system: ActorSystem, tokenValidator: TokenValidator[U]) extends LazyLogging {
  implicit val materializer = Materializer.apply(system)
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

  def messageToEngineIoEnvelopeProcessor():Processor[Message, EngineIOEnvelope] = TryFunctionProcessor[Message, EngineIOEnvelope](unwrap, "m2e")

  def messageToEngineIoEnvelopeSink(processor:Processor[Message, EngineIOEnvelope]):Sink[Message, NotUsed] = Sink.fromSubscriber(processor)

  def messageToEngineIoEnvelopeSource(processor:Processor[Message, EngineIOEnvelope]):Source[EngineIOEnvelope, NotUsed] = Source.fromPublisher(processor)

  def messageToEngineIoEnvelopeFlow():Flow[Message, EngineIOEnvelope, NotUsed] = Flow.fromProcessor(() => messageToEngineIoEnvelopeProcessor())

  def engineIoEnvelopeToCommandProcessor(userCtx:UserContext[U]):Processor[EngineIOEnvelope, Command] = FunctionProcessor[EngineIOEnvelope, Command]((e:EngineIOEnvelope) => e.messageType.toActorMessage(userCtx, e), "e2c")

  def engineIoEnvelopeToCommandSink(processor:Processor[EngineIOEnvelope, Command]):Sink[EngineIOEnvelope, NotUsed] = Sink.fromSubscriber(processor)

  def engineIoEnvelopeToCommandSource(processor:Processor[EngineIOEnvelope, Command]):Source[Command, NotUsed] = Source.fromPublisher(processor)

  def engineIoEnvelopeToCommandFlow(processor:Processor[EngineIOEnvelope, Command]):Flow[EngineIOEnvelope, Command, NotUsed] = Flow.fromProcessor(() => processor)

  def socketIoProcessor():Processor[EngineIOEnvelope, EngineIOEnvelope] = new PartialFunctionProcessor(handleEngineIOHeartbeat)

  def shortCircuit(src:Source[EngineIOEnvelope, NotUsed], sink:Sink[EngineIOEnvelope, NotUsed]) = {
    ShortCircuit[EngineIOEnvelope, EngineIOEnvelope](src,
      sink,
      handleEngineIOMessages,
      handleEngineIOHeartbeat
    )
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
                    val(sink1, source1) = sinkAndSourceOf(messageToEngineIoEnvelopeProcessor())
                    val(sink2, source2) = sinkAndSourceOf(engineIoEnvelopeToCommandProcessor(userCtx))

                    source2.to(Sink.futureSink[Command, NotUsed](fSink)).run()

//                    val sink:Sink[Message, NotUsed] = messageToEngineIoEnvelopeProcessor().
//                                                        via(engineIoEnvelopeToCommandProcessor(userCtx)).
//                                                        to(Sink.futureSink[Command, NotUsed](fSink))

                    val (source, graph) = shortCircuit(source1, sink2)
                    val f = Flow.fromSinkAndSource(sink1, source.map(v => TextMessage.Strict(v.toString)))
                    graph.run()
                    f
//                    Flow.fromSinkAndSource(sink1, source1)
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
                      }.map {r => r.namespaces.headOption.map(EngineIOEnvelope.connect(_)).getOrElse(EngineIOEnvelope.connect())}
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