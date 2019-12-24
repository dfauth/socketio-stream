package com.github.dfauth.socketio

import java.util

import com.typesafe.scalalogging.LazyLogging
import io.socket.client.{IO, Manager, Socket}
import io.socket.emitter.Emitter
import io.socket.engineio.client.Transport
import org.scalatest.{Args, FlatSpec, Matchers}

import scala.collection.mutable
import scala.collection.JavaConverters._

class TestSpec extends FlatSpec with Matchers with LazyLogging  {

  val token = "jkdfghkldfjghkdh_big_fat_token_ksdhklhgkdfh"

  val opts: IO.Options = new IO.Options
  opts.forceNew = true
  // opts.query = "sid=jkdfghkldfjghkdh_big_fat_token_ksdhklhgkdfh"

  val tOpts = new Transport.Options
  opts.transportOptions = new java.util.HashMap[String, Transport.Options]()
  opts.transportOptions.put("polling", tOpts)

  tOpts.hostname = "localhost"
  tOpts.port = 9000
  tOpts.path = "/socket.io/"
  tOpts.query = new util.HashMap[String, String]()

  // tOpts.query.put("sid", "jkdfghkldfjghkdh_big_fat_token_ksdhklhgkdfh")

  /**
  transportOptions: {
            polling: {
              extraHeaders: {
                'x-auth': 'jkdfghkldfjghkdh_big_fat_token_ksdhklhgkdfh'
              }
            }
          }
    */

  "a socketio client" should "work" in {

    val socket = IO.socket("http://127.0.0.1:8081", opts)
    socket.on(Socket.EVENT_CONNECTING , (args: Any) => {
      logger.info(s"connecting args: ${args}")
    }).on(Socket.EVENT_CONNECT, (args: Any) => {
      logger.info(s"connected args: ${args}")
    }).on(Socket.EVENT_CONNECT_ERROR, (args: Array[Object]) => {
      logger.info(s"connect error args: ${args}")
      val e = args(0).asInstanceOf[Exception]
      logger.error(e.getMessage, e)
    }).on(Socket.EVENT_CONNECT_TIMEOUT, (args: Any) => {
      logger.info(s"connect timeout args: ${args}")
    }).on("event", (args: Any) => {
      logger.info(s"event received args: ${args}")
    }).on(Socket.EVENT_DISCONNECT, (args: Any) => {
      logger.info(s"disconnected args: ${args}")
    }).on(Socket.EVENT_ERROR, (args: Any) => {
      logger.info(s"Oops args: ${args}")
    })

    socket.io.on(Manager.EVENT_TRANSPORT, (args: Array[Object]) => {
      val transport = args(0).asInstanceOf[Transport]
      transport.on(Transport.EVENT_REQUEST_HEADERS, (args: Array[Object]) => {
        @SuppressWarnings(Array("unchecked")) val headers = args(0).asInstanceOf[java.util.Map[String, java.util.List[String]]]
        // modify request headers
        headers.put("x-auth", List(token).asJava)
      })
      //        transport.on(Transport.EVENT_RESPONSE_HEADERS, new Emitter.Listener() {
      //          override def call(args: Any*): Unit = {
      //            @SuppressWarnings(Array("unchecked")) val headers = args(0).asInstanceOf[Nothing]
      //            // access response headers
      //            val cookie = headers.get("Set-Cookie").get(0)
      //          }
      //        })
    })

    socket.connect()

    Thread.sleep(10 * 1000)


  }

}

