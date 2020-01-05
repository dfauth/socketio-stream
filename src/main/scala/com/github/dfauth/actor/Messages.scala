package com.github.dfauth.actor

import akka.actor.typed.ActorRef

trait Command {
  val id:String
}
trait SupervisorCommand extends Command
//case object CreateSession extends Command
case class AddNamespace(id:String, namespace:String) extends Command
case class EndSession(id:String) extends Command
case class FetchSession(id:String, replyTo:ActorRef[FetchSessionReply]) extends Command
case class FetchSessionReply(id:String, namespace:String) extends Command
case class ErrorMessage(id:String, t:Throwable) extends Command
