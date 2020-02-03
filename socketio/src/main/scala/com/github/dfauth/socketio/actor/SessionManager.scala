package com.github.dfauth.socketio.actor

import akka.NotUsed
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{Behavior, PostStop, Signal}
import akka.stream.Materializer
import akka.stream.scaladsl.{BroadcastHub, Flow, Keep, MergeHub, Sink, Source}
import akka.stream.typed.scaladsl.ActorSink
import com.github.dfauth.auth.AuthenticationContext
import com.github.dfauth.socketio
import com.github.dfauth.socketio.reactivestreams.Processors._
import com.github.dfauth.socketio._
import com.github.dfauth.socketio.utils.StreamUtils

object SessionManager {
  def apply[U](userCtx:AuthenticationContext[U], flowFactories:Seq[FlowFactory[U]]): Behavior[Command] = Behaviors.setup[Command](context => new SessionManager(context, userCtx, flowFactories))
}

class SessionManager[U](ctx: ActorContext[Command], userCtx:AuthenticationContext[U], flowFactories:Seq[FlowFactory[U]]) extends AbstractBehavior[Command](ctx) {

  implicit val mat = Materializer(ctx.system)
  val logger = ctx.log
  logger.info(s"session manager started with user ctx: ${userCtx}")

  val (sink0, source0) = sinkToSource[Command]
  val (sink, source) = sinkToSource[Command]
  val streamSink:Sink[Command, NotUsed] = MergeHub.source[Command](16).to(sink).run()
  val streamSource:Source[Command, NotUsed] = source0.toMat(BroadcastHub.sink[Command](16))(Keep.right).run()
  streamSource.filter(_.namespace == "").runWith(ActorSink.actorRef(ctx.self,
    EndSession(userCtx.token),
    t => ErrorMessage(userCtx.token, t)
  ))

  def outbound(namespace:String):Event => Command = (m:Event) => m match {
    case a:Acknowledgement => MessageCommand(userCtx.token, namespace, socketio.EventWrapper(m.eventId, m, Some(a.ackId)))
    case _ => MessageCommand(userCtx.token, namespace, socketio.EventWrapper(m.eventId, m, None))
  }
  def inbound:Command => StreamMessage = (c:Command) => c match {
    case e:AckCommand => e.payload.map { new AcknowledgeableEventMessage(_, e.ackId)}.getOrElse(new AcknowledgeableEventMessage("missing ack message", e.ackId))
    case x => {
      val e = new IllegalArgumentException(s"Unexpected message type: ${x}")
      logger.error(e.getMessage, e)
      AcknowledgeableEventMessage(e.getMessage, 0)
    }
  }

  flowFactories.foreach { f =>
    val flow = f.create(userCtx)
    flow.map(StreamUtils.loggingFn("WOOZ outbound")).map(outbound(f.namespace)).toMat(streamSink)(Keep.both)
    streamSource.map(StreamUtils.loggingFn("WOOZ inbound")).filter(_.namespace == f.namespace).map(inbound).viaMat(flow)(Keep.both)
  }

  override def onMessage(msg: Command): Behavior[Command] = {
    ctx.log.info(s"session manager received message ${msg}")
    msg match {
      case AddNamespace(id, namespace) => {
        Behaviors.unhandled // cannot support this in stateless polling model
      }
      case FetchSessionCommand(id, replyTo) => {
        replyTo ! FetchSessionReply(id, flowFactories.map {_.namespace}, ctx.self, sink0, source)
        Behaviors.same
      }
      case EventCommand(id, namespace, payload) => {
        Behaviors.same
      }
      case AckCommand(id, nsp, ackId, payload) => {
        ctx.log.info(s"message with id ${ackId} in namespace ${nsp} acked")
        Behaviors.same
      }
      case EndSession(id) => {
        ctx.log.info(s"sessionManager: session ${id} stopped")
        Behaviors.stopped
      }
      case x => {
        ctx.log.error(s"received unhandled message ${x}")
        Behaviors.unhandled
      }
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("session manager stopped")
      this
  }
}

