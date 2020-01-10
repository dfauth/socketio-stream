package com.github.dfauth.actor

import akka.NotUsed
import akka.actor.typed.ActorRef
import akka.stream.scaladsl.{Sink, Source}
import com.github.dfauth.actor.ActorUtils.AskSupport
import com.github.dfauth.socketio.UserContext

trait Command {
  val id:String
}

trait SupervisorCommand extends Command
case class CreateSession[U](userCtx:UserContext[U], namespaces:Iterable[String]) extends AskSupport[CreateSessionCommand[U], CreateSessionReply] {
  override def apply(ref:ActorRef[CreateSessionReply]) = CreateSessionCommand(userCtx.token, userCtx, namespaces, ref)
}
case class CreateSessionReply(id:String) extends Command
case class CreateSessionCommand[U](id:String, userCtx:UserContext[U], namespaces:Iterable[String], replyTo: ActorRef[CreateSessionReply]) extends Command with AskCommand[CreateSessionReply]
case class AddNamespace(id:String, namespace:String) extends Command
case class EndSession(id:String) extends Command
case class FetchSession(id:String) extends AskSupport[FetchSessionCommand, FetchSessionReply] {
  override def apply(ref:ActorRef[FetchSessionReply]) = FetchSessionCommand(id, ref)
}
case class FetchSessionCommand(id:String, replyTo:ActorRef[FetchSessionReply]) extends Command with AskCommand[FetchSessionReply]
case class FetchSessionReply(id:String, namespaces:Iterable[String], ref:ActorRef[Command], src:Source[Command, NotUsed]) extends Command
case class ErrorMessage(id:String, t:Throwable) extends Command
case class EventCommand(id:String, namespace:String, payload:Option[String] = None) extends Command
case class MessageCommand[T](id:String, namespace:String, payload:T) extends Command
case class StreamComplete(id:String) extends Command
