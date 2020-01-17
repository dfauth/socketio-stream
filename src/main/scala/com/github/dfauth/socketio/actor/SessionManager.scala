package com.github.dfauth.socketio.actor

import akka.NotUsed
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{Behavior, PostStop, Signal}
import akka.stream.{FlowShape, Graph, Materializer, SourceShape}
import akka.stream.scaladsl.{BidiFlow, BroadcastHub, Flow, GraphDSL, Keep, MergeHub, RunnableGraph, Sink, Source}
import akka.stream.typed.scaladsl.ActorSink
import com.github.dfauth.socketio
import com.github.dfauth.socketio.Processors._
import com.github.dfauth.socketio._
import com.github.dfauth.socketio.utils.StreamUtils

object SessionManager {
  def apply[U](userCtx:UserContext[U], flowFactories:Seq[FlowFactory]): Behavior[Command] = Behaviors.setup[Command](context => new SessionManager(context, userCtx, flowFactories))
}

class SessionManager[U](ctx: ActorContext[Command], userCtx:UserContext[U], flowFactories:Seq[FlowFactory]) extends AbstractBehavior[Command](ctx) {

  implicit val mat = Materializer(ctx.system)
  val logger = ctx.log
  logger.info(s"session manager started with user ctx: ${userCtx}")

  val (sink0, source0) = sinkToSource[Command]
  val streamSource:Source[Command, NotUsed] = source0.toMat(BroadcastHub.sink[Command](16))(Keep.right).run()

  val (sink, source) = sinkToSource[Command]
  val streamSink:Sink[Command, NotUsed] = MergeHub.source[Command](16).to(sink).run()
  val streamFlow:Flow[Command, Command, NotUsed] = Flow.fromSinkAndSource(streamSink, streamSource)
  streamSource.filter(_.namespace == "").runWith(ActorSink.actorRef(ctx.self,
    StreamComplete(userCtx.token),
    t => ErrorMessage(userCtx.token, t)
  ))

  def outbound(namespace:String):Ackable with Eventable => Command = (m:Ackable with Eventable) => MessageCommand(userCtx.token, namespace, socketio.EventWrapper(m.eventId, m, Some(m.ackId)))
  def inbound:Command => Ackable with Eventable = (c:Command) => {
    new Ackable with Eventable {
      override val ackId: Int = 0
      override val eventId:String = "event"
    }
  }

  def bidiFlow(namespace:String): BidiFlow[Ackable with Eventable, Command, Command, Ackable with Eventable, NotUsed] = BidiFlow.fromFunctions(
    outbound(namespace),
    inbound
  )

  def initializeSources() = {
    flowFactories.foreach { f =>
      val (sink, source) = f.create
      source.map(outbound(f.namespace)).runWith(streamSink)
      streamSource.filter(_.namespace == f.namespace).map(inbound).runWith(sink)
    }
  }

  override def onMessage(msg: Command): Behavior[Command] = {
    ctx.log.info(s"session manager received message ${msg}")
    msg match {
      case AddNamespace(id, namespace) => {
        Behaviors.unhandled // cannot support this in stateless polling model
      }
      case FetchSessionCommand(id, replyTo) => {
        replyTo ! FetchSessionReply(id, flowFactories.map {_.namespace}, ctx.self, sink0, source)
        initializeSources()
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
        ctx.log.info(s"session ${id} stopped")
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

