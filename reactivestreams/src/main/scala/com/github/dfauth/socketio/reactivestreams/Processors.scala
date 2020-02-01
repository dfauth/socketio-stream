package com.github.dfauth.socketio.reactivestreams

import java.util.concurrent.atomic.AtomicBoolean

import akka.NotUsed
import akka.stream.scaladsl.{Sink, Source}
import com.github.dfauth.socketio.utils.TryCatchUtils._
import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.{Processor, Subscriber, Subscription}

import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

object Processors {

  def mapSink[R, T](f:R => T, sink: Sink[T,NotUsed]):Sink[R, NotUsed] = {
    val (sink0, src0) = sinkAndSourceOf(FunctionProcessor(f))
    src0.to(sink)
    sink0
  }

  def sourceFromSinkConsumer[T:ClassTag](sinkConsumer: Sink[T,NotUsed] => Unit):Source[T, NotUsed] = {
    val (sink, src) = sinkToSource[T]
    sinkConsumer(sink)
    src
  }

  def sinkToSource[T:ClassTag]:(Sink[T, NotUsed], Source[T, NotUsed]) = sinkAndSourceOf(FunctionProcessor[T]())

  def sinkAndSourceOf[I,O](processor:Processor[I,O]) = {
    val sink:Sink[I, NotUsed] = Sink.fromSubscriber(processor)
    val source:Source[O, NotUsed] = Source.fromPublisher(processor)
    (sink, source)
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
    subscription.foreach(init(_))
  }

  protected def withSubscription(q: Subscription): Subscription = q

  protected override def init(q:Subscription): Unit = {
    subscriber.foreach(s => {
      s.onSubscribe(withSubscription(q))
      logger.info(withName("subscribed"))
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
      }
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
      }
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
      }
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
      }
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
      }
    })
  }
}

object ControllingProcessor {
  def apply[T]() = new ControllingProcessor[T]()
  def apply[T](name:String) = new ControllingProcessor[T](Some(name))
}

class ControllingProcessor[T](override val name:Option[String] = None) extends FunctionProcessor[T, T](t => t) {

  private val _toggle = new AtomicBoolean(true)

  private var controllingSubscription:Option[ControllingSubscription] = None

  def toggle = {
    if(_toggle.get()) {
      off
    } else {
      on
    }
    this
  }

  def on = {
    _toggle.set(true)
    controllingSubscription.map { _.resume()}
    this
  }

  def off = {
    _toggle.set(false)
    this
  }

  def terminate = {
    _toggle.set(false)
    controllingSubscription.map{s => s.cancel()}
    this
  }

  override def withSubscription(q: Subscription): Subscription = {
    controllingSubscription = Some(ControllingSubscription(q, _toggle))
    controllingSubscription.get
  }
}

case class ControllingSubscription(nested:Subscription, toggle:AtomicBoolean) extends Subscription with LazyLogging {

  private var pending:Option[Long] = None

  def resume():Unit = {
    pending.map {l => {
      nested.request(l)
      logger.info(s"requested pending demand ${l}")
    }}
  }

  override def request(l: Long): Unit = {
    if(toggle.get()) {
      nested.request(l)
    } else {
      // save it
      pending = Some(l)
      logger.info(s"parked pending demand ${pending}")
    }
  }

  override def cancel(): Unit = nested.cancel()
}