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
import com.github.dfauth.utils.{MergingGraph, ShortCircuit}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.Processor

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

class SocketIoStream[U](system: ActorSystem, tokenValidator: TokenValidator[U], sourceFactories:Seq[SourceFactory]) extends LazyLogging {
  implicit val materializer = Materializer.apply(system)
  val config = SocketIOConfig(ConfigFactory.load())
  val route = subscribe
  val typedSystem = TypedActorSystem[Command](Supervisor(sourceFactories), "socket_io")
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

  def commandToEngineIoEnvelope:Command => EngineIOEnvelope = (c:Command) => c match {
    case MessageCommand(id, namespace, payload) => payload match {
      case EventWrapper(_, _, optAckId) => EngineIOEnvelope(Msg, Some(SocketIOEnvelope.event(namespace, payload.toString, optAckId)))
      case _ => EngineIOEnvelope(Msg, Some(SocketIOEnvelope.event(namespace, payload.toString)))
    }
  }

  def engineIoEnvelopeToTextMessage:EngineIOEnvelope => TextMessage = (e:EngineIOEnvelope) => e match {
    case x => TextMessage.Strict(e.toString)
//    case EngineIOEnvelope(Pong, _) =>  TextMessage.Strict(e.toString)
//    case EngineIOEnvelope(Msg, Some(SocketIOEnvelope(Event, Some(SocketIOPacket(namespace, Some(payload)))))) =>  TextMessage.Strict(e.toString)
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

  def merge(src1:Source[EngineIOEnvelope, NotUsed], src2:Source[EngineIOEnvelope, NotUsed]) = MergingGraph(src1, src2)

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
                  val fRef = askActor {
                    supervisor ? FetchSession(id)
                  }
                  val fSrc:Future[Source[Command, NotUsed]] = fRef.map { reply => reply.src}
                  val fSink:Future[Sink[Command, NotUsed]] = fRef.map {reply =>
                    ActorSink.actorRef[Command](reply.ref,
                      StreamComplete(id),
                      t => ErrorMessage(id, t)
                    )
                  }
                  handleWebSocketMessages {
                    val(sink1, source1) = sinkAndSourceOf(messageToEngineIoEnvelopeProcessor())
                    val(sink2, source2) = sinkAndSourceOf(engineIoEnvelopeToCommandProcessor(userCtx))

                    source2.to(Sink.futureSink[Command, NotUsed](fSink)).run()

                    val (source, graph1) = shortCircuit(source1, sink2)
                    val (mergedSource, graph2) = MergingGraph[EngineIOEnvelope](source, Source.futureSource(fSrc).map(commandToEngineIoEnvelope).asInstanceOf[Source[EngineIOEnvelope, NotUsed]])
                    graph1.run()
                    graph2.run()
                    Flow.fromSinkAndSource(sink1, mergedSource.map(engineIoEnvelopeToTextMessage))
                  }
                }
                case activeTransport@Polling => {
                  val f:Future[EngineIOPackets] = sid match {
                    case None => {
                      askActor {
                        supervisor ? CreateSession(userCtx)
                      }.map {r => EngineIOPackets(EngineIOEnvelope.open(userCtx.token, config, activeTransport)) }
                    }
                    case Some(v) => {
                      askActor{
                        supervisor ? FetchSession(v)
                      }.map { r =>
                        EngineIOPackets(r.namespaces.map(n => EngineIOEnvelope.connect(n)).toSeq:_*)
                      }
                    }
                  }
                  complete(octetStream(Source.fromPublisher(DelayedClosePublisher(f.map {v => ByteString(v.toBytes)}, config.longPollTimeout))))
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
  def apply[U](system: ActorSystem, tokenValidator: TokenValidator[U], sourceFactories:Seq[SourceFactory]): SocketIoStream[U] = new SocketIoStream(system, tokenValidator, sourceFactories)
}