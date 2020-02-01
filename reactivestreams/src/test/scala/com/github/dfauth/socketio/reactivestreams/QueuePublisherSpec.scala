package com.github.dfauth.socketio.reactivestreams

import java.util
import java.util.concurrent.Executors

import com.github.dfauth.reactivestreams.AssertingSubscriber

import scala.compat.java8.FutureConverters._
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FlatSpec, Matchers}

import scala.concurrent.Await
import scala.concurrent.duration._

class QueuePublisherSpec extends FlatSpec with Matchers with LazyLogging {

  "A QueuePublisher" should "publish an object added to the queue" in {
    val q = new util.ArrayDeque[Long](10)

    val publisher = QueuePublisher[Long](q)(Executors.newSingleThreadScheduledExecutor())

    val subscriber = new AssertingSubscriber[Long]()
    val whenComplete = subscriber.asserter.onComplete()

    publisher.subscribe(subscriber)
    val ref = 1L
    q.offer(ref)
    publisher.stopWhenEmpty
    Await.result(whenComplete.toScala, 10.seconds)
    subscriber.assertReceived(ref).only
  }

  def sleep(i: Long) = Thread.sleep(i)

  "A QueuePublisher" should "wait until an object is added to the queue" in {
    val q = new util.ArrayDeque[Long](10)

    val publisher = QueuePublisher[Long](q)(Executors.newSingleThreadScheduledExecutor())

    val subscriber = new AssertingSubscriber[Long]()
    val whenComplete = subscriber.asserter.onComplete()

    publisher.subscribe(subscriber)
    val ref = 1L

    subscriber.assertNothingReceived().yet
    sleep(1000)
    subscriber.assertNothingReceived().yet

    q.offer(ref)
    publisher.stopWhenEmpty
    Await.result(whenComplete.toScala, 10.seconds)
    subscriber.assertReceived(ref).only
  }
}
