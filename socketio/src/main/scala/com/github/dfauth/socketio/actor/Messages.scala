package com.github.dfauth.socketio.actor

import akka.NotUsed
import akka.actor.typed.ActorRef
import akka.actor.typed.receptionist.ServiceKey
import akka.stream.scaladsl.{Sink, Source}
import com.github.dfauth.auth.AuthenticationContext
import com.github.dfauth.socketio.actor.ActorUtils.AskSupport
import com.github.dfauth.socketio.{Acknowledgement, Event}

import scala.reflect.ClassTag

trait Command {
  val id:String
  val namespace:String
}

trait ControlCommand extends Command {
  override val namespace: String = ""
}
case class CreateSession[U](userCtx:AuthenticationContext[U]) extends AskSupport[CreateSessionCommand[U], CreateSessionReply] {
  override def apply(ref:ActorRef[CreateSessionReply]) = CreateSessionCommand(userCtx.token, userCtx, ref)
}
trait CreateSessionReply extends ControlCommand
case class SessionCreated(id:String) extends CreateSessionReply
case class SessionExists(id:String) extends CreateSessionReply
case class SessionCreationError(id:String, t:Throwable) extends CreateSessionReply
case class CreateSessionCommand[U](id:String, userCtx:AuthenticationContext[U], replyTo: ActorRef[CreateSessionReply]) extends ControlCommand with AskCommand[CreateSessionReply]
case class AddNamespace(id:String, toBeAdded:String) extends ControlCommand
case class EndSession(id:String) extends ControlCommand
case class FetchSession(id:String) extends AskSupport[FetchSessionCommand, FetchSessionReply] {
  override def apply(ref:ActorRef[FetchSessionReply]) = FetchSessionCommand(id, ref)
}
case class FetchSessionCommand(id:String, replyTo:ActorRef[FetchSessionReply]) extends ControlCommand with AskCommand[FetchSessionReply]
case class FetchSessionReply(id:String, namespaces:Iterable[String], ref:ActorRef[Command], sink:Sink[Command, NotUsed], src:Source[Command, NotUsed]) extends ControlCommand
class ErrorMessage(override val id:String, msg:String, t:Option[Throwable]) extends ControlCommand
case class EventCommand(id:String, namespace:String, payload:Option[String] = None) extends Command
case class AckCommand(id:String, namespace:String, ackId:Long, payload:Option[String] = None) extends Command
case class MessageCommand[T](id:String, namespace:String, payload:T) extends Command
case class EndStream(id:String, namespace:String) extends Command
class ActorMonitorCommand(key:ServiceKey[Command]) extends ControlCommand {
  val id:String = key.id
}
case class ActorTerminatedCommand(key:ServiceKey[Command]) extends ActorMonitorCommand(key)
case class ActorCreatedCommand(key:ServiceKey[Command], ref:ActorRef[Command]) extends ActorMonitorCommand(key)

object ErrorMessage {
  def apply(id:String, t:Throwable) = new ErrorMessage(id, t.getMessage, Some(t))
  def apply(id:String, msg:String) = new ErrorMessage(id, msg, None)
}

case class AcknowledgeableEventMessage(msg:String, ackId:Long) extends Event with Acknowledgement {
  val eventId:String = "ack"
  override def toString: String = s""""${msg} ack id: ${ackId}""""
}

