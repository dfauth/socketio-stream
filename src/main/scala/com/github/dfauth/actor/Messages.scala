package com.github.dfauth.actor

import akka.actor.typed.ActorRef
import com.github.dfauth.actor.ActorUtils.AskSupport
import com.github.dfauth.socketio.UserContext

trait Command {
  val id:String
}

trait SupervisorCommand extends Command
case class CreateSession[U](userCtx:UserContext[U]) extends Command {
  val id:String = userCtx.token
}
case class AddNamespace(id:String, namespace:String) extends Command
case class EndSession(id:String) extends Command
case class FetchSession(id:String) extends AskSupport[FetchSessionCommand, FetchSessionReply] {
  override def apply(ref:ActorRef[FetchSessionReply]) = FetchSessionCommand(id, ref)
}
case class FetchSessionCommand(id:String, replyTo:ActorRef[FetchSessionReply]) extends Command with AskCommand[FetchSessionReply]
case class FetchSessionReply(id:String, namespace:String) extends Command
case class ErrorMessage(id:String, t:Throwable) extends Command
