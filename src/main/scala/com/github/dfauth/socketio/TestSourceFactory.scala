package com.github.dfauth.socketio
import java.util.concurrent.atomic.AtomicInteger

import akka.NotUsed
import akka.actor.Cancellable
import akka.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory
import com.github.dfauth.utils.StreamUtils._
import scala.concurrent.duration._

case class Blah(ackId:Int) extends Ackable with Eventable {
  val eventId:String = "left"
  override def toString: String = ackId.toString
}
case class BlahChar(c:Char, ackId:Int) extends Ackable with Eventable {
  val eventId:String = "right"
  override def toString: String = s""""${c.toString}""""
}

case class TestSourceFactory(namespace:String, f:()=>Ackable with Eventable) extends SourceFactory {

  val i = new AtomicInteger()

  override def create[T >: Ackable with Eventable]: Source[T, Cancellable] = {

    Source.tick(ONE_SECOND, ONE_SECOND, f).map {g => g() }
//    namespace match {
//      case "/rfq" => Source.tick(ONE_SECOND, ONE_SECOND, () => i.incrementAndGet()).map { (f:()=>Int) => new Blah(f()) }
//      case "/orders" => {
//        Source.tick(ONE_SECOND, 900 millis,() => i.incrementAndGet()).map {(f:()=>Int) => {
//          val c = ('A'.toInt + f()%26).toChar
//          new BlahChar(c,f())
//        }
//      }}
//    }
  }
}
