package com.github.dfauth.socketio

import java.util.concurrent.atomic.AtomicBoolean

import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source}
import com.github.dfauth.socketio.utils.TryCatchUtils._
import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.{Processor, Subscriber, Subscription}

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object Processors {

  def sinkToSource[T:ClassTag]:(Sink[T, NotUsed], Source[T, NotUsed]) = sinkAndSourceOf(FunctionProcessor[T]())

  def sinkAndSourceOf[I,O](processor:Processor[I,O]) = {
    val sink:Sink[I, NotUsed] = Sink.fromSubscriber(processor)
    val source:Source[O, NotUsed] = Source.fromPublisher(processor)
    (sink, source)
  }
}

trait AbstractBaseSubscriber[T] extends Subscriber[T] with LazyLogging {

  val name:Option[String] = None
  var subscription:Option[Subscription] = None

  def onSubscribe(s: Subscription): Unit = {
    subscription = Some(s)
    logger.debug(withName("onSubscribe"))
    init()
  }

  def onNext(t: T): Unit

  def onError(t: Throwable): Unit = {
    logger.error(t.getMessage, t)
  }

  def onComplete(): Unit

  def withName(str: String): String = name.map {s => s"${s} ${str}"}.getOrElse {str}

  protected def init(): Unit = {
    subscription.map(s => s.request(Int.MaxValue))
  }
}

trait AbstractBaseProcessor[I, O] extends AbstractBaseSubscriber[I] with Processor[I, O] with LazyLogging {

  var subscriber:Option[Subscriber[_ >: O]] = None
  val flag = new AtomicBoolean(false)

  override def onError(t: Throwable): Unit = {
    super.onError(t)
    subscriber.map(_.onError(t))
  }

  override def onComplete(): Unit = subscriber.map(_.onComplete())

  override def subscribe(s: Subscriber[_ >: O]): Unit = {
    subscriber = Some(s)
    logger.debug(withName("subscribe"))
    init()
  }

  protected override def init(): Unit = {
    subscriber.foreach(s => {
      subscription.foreach(q => {
        if(flag.compareAndSet(false, true)) {
          s.onSubscribe(q)
          logger.info(withName("subscribed"))
        } else {
          //logger.error(withName("unexpectedly already subscribed"))
          throw new RuntimeException(withName("unexpectedly already subscribed"))
        }
      })
    })
  }
}

object FunctionProcessor {
  def apply[I]() = new FunctionProcessor[I,I](x => x)
  def apply[I](name:String) = new FunctionProcessor[I,I](x => x, Some(name))
  def apply[I,O](f:I => O) = new FunctionProcessor[I,O](f)
  def apply[I,O](f:I => O, name:String) = new FunctionProcessor[I,O](f, Some(name))
}

class FunctionProcessor[I, O](val f:I => O, override val name:Option[String] = None) extends AbstractBaseProcessor[I, O] {
  override def onNext(i: I): Unit = {
    subscriber.foreach(s => {
      tryCatch {
        val o = f(i)
        logger.info(withName(s"onNext: ${i} => ${o}"))
        s.onNext(o)
      }()
    })
  }
}

class OptionFunctionProcessor[I, O](val f:I => Option[O], override val name:Option[String] = None) extends AbstractBaseProcessor[I, O] {
  override def onNext(i: I): Unit = {
    subscriber.foreach(s => {
      tryCatch {
        val o = f(i).map { o =>
          logger.info(withName(s"onNext: ${i} => ${o}"))
          s.onNext(o)
        }
      }()
    })
  }
}

class FilteringProcessor[I](val f:I => Boolean, override val name:Option[String] = None) extends AbstractBaseProcessor[I, I] {
  override def onNext(i: I): Unit = {
    subscriber.foreach(s => {
      tryCatch {
        if(f(i)) {
          s.onNext(i)
        }
      }()
    })
  }
}

object TryFunctionProcessor {
  def apply[I,O](f:I => Try[O]) = new TryFunctionProcessor[I,O](f)
  def apply[I,O](f:I => Try[O], name:String) = new TryFunctionProcessor[I,O](f, Some(name))
}

class TryFunctionProcessor[I, O](val f:I => Try[O], override val name:Option[String] = None) extends AbstractBaseProcessor[I, O] {
  override def onNext(i: I): Unit = {
    subscriber.foreach(s => {
      tryCatch[Unit] {
        f(i) match {
          case Success(o) => {
            logger.info(withName(s"onNext: ${i} => ${o}"))
            s.onNext(o)
          }
          case Failure(t) => {
            logger.error(withName(t.getMessage), t)
            s.onError(t)
          }
        }
      } ()
    })
  }
}

object PartialFunctionProcessor {
  def apply[I,O](f:PartialFunction[I,O]) = new PartialFunctionProcessor[I,O](f)
  def apply[I,O](f:PartialFunction[I,O], name:String) = new PartialFunctionProcessor[I,O](f, Some(name))
}

class PartialFunctionProcessor[I, O](val f:PartialFunction[I, O], override val name:Option[String] = None) extends AbstractBaseProcessor[I, O] {
  override def onNext(i: I): Unit = {
    subscriber.foreach(s => {
      tryCatch {
        if(f.isDefinedAt(i)) {
          val o = f(i)
          logger.info(withName(s"onNext: ${i} => ${o}"))
          s.onNext(o)
        }
      }()
    })
  }
}

class ControllingProcessor[T](override val name:Option[String] = None) extends FunctionProcessor[T, T](t => t) {

  private val _toggle = new AtomicBoolean(true)

  def toggle:Unit = {
    if(_toggle.get()) {
      off
    } else {
      on
    }
  }

  def on:Unit = {
    _toggle.getAndSet(true)
    subscription.map{s => s.request(Long.MaxValue)}
  }

  def off:Unit = {
    _toggle.getAndSet(false)
    subscription.map{s => s.cancel()}
  }

  override def onNext(t:T): Unit = {
    if(_toggle.get()) {
      super.onNext(t);
    }
  }
}
