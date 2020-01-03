package com.github.dfauth.socketio

import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.unmarshalling.{FromRequestUnmarshaller, Unmarshaller}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.ByteString
import com.github.dfauth.engineio._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

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


  def unwrap: Message => Future[EngineIOEnvelope] = a => Future {
    a match {
      case TextMessage.Strict(b) => {
        val env = EngineIOEnvelope.fromBytes(b.getBytes)
        logger.info(s"bytes: ${b}, envelope: ${env}")
        env.getOrElse(throw new IllegalArgumentException(s"Invalid message format: ${b}"))
      }
      case x => throw new IllegalArgumentException(s"Unexpected message: ${x}")
    }
  }

  def probe: EngineIOEnvelope => Option[EngineIOEnvelope] = {
    case EngineIOEnvelope(msgType, None) => {
      msgType match {
        case Ping => Some(EngineIOEnvelope.heartbeat())
        case Upgrade => None // ignore
      }

    }
    case EngineIOEnvelope(msgType, data) => {
      (msgType, data) match {
        case (Ping, Some(EngineIOStringPacket(m))) => Some(EngineIOEnvelope.heartbeat(Some(m)))
      }
    }
  }

  implicit val decoder:FromRequestUnmarshaller[EngineIOEnvelope] = new Unmarshaller[HttpRequest, EngineIOEnvelope](){
    override def apply(req: HttpRequest)(implicit ec: ExecutionContext, materializer: Materializer): Future[EngineIOEnvelope] = {
      val p = Promise[EngineIOEnvelope]()
      Future({
        val str = req.entity.dataBytes.runFold(new String)((acc, byteString)=> acc ++ byteString.utf8String)
        str.onComplete({
          case Success(s) => EngineIOEnvelope.fromString(s).map { p.success(_)} getOrElse {p.failure(new RuntimeException("Failed to collect bytes"))}
          case Failure(t) => p.failure(t)
        })(global)
      })(global)
      p.future
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
                    val processor = new HandshakeProcessor[Message, Message](tmp, (_,_) => true)
                    val handshakeSink:Sink[Message, NotUsed] = Sink.fromSubscriber(processor)
                    val handshakeSrc:Source[Message, NotUsed] = Source.fromPublisher(processor)
                    val handshakeFlow:Flow[Message, Message, NotUsed] = Flow.fromProcessor(() => processor)
                    val heartbeat = TextMessage.Strict(EngineIOPackets(EngineIOEnvelope.heartbeat()).toString)
                    val heartbeatInterval = FiniteDuration(config.pingInterval.get(ChronoUnit.SECONDS),TimeUnit.SECONDS)
                    val nested = Flow.fromSinkAndSource(Sink.ignore, Source.empty)
                    .keepAlive(heartbeatInterval, () => heartbeat)
                    val concatSrc:Source[Message, NotUsed] = Source.empty[Message].flatMapConcat(_ => handshakeSrc).flatMapConcat(_ => Source.tick(heartbeatInterval, heartbeatInterval,heartbeat))
//                    Flow.fromSinkAndSource(handshakeSink, concatSrc)
                    handshakeFlow
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
            logger.info(s"${e} ${e.messageType} ${e.data}")
            complete(HttpResponse(StatusCodes.OK))
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