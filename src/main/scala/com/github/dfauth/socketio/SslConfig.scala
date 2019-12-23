package com.github.dfauth.socketio

import java.io.InputStream
import java.security.{KeyStore, SecureRandom}

import akka.http.scaladsl.ConnectionContext
import javax.inject.Named
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import org.slf4j.{Logger, LoggerFactory}

import scala.util.{Failure, Success, Try}

case class SslConfig(@Named("socketio.http.useSSL") useSSL: Boolean = false,
                     @Named("socketio.http.ssl.cert.password") password: String = "changeit",
                     @Named("socketio.http.ssl.cert.p12") certFile: String = "",
                     @Named("socketio.http.ssl.hsts.expire.days") days: Int = 365) {

  val logger:Logger  = LoggerFactory.getLogger(classOf[SslConfig])

  def expireDays():Int = days

  def isEnabled(): Boolean = useSSL

  def getConnectionContext():ConnectionContext = {
    if(useSSL) {
      useTLS().getOrElse(useDefault())
    } else {
      useDefault()
    }
  }

  private def useDefault(): ConnectionContext = ConnectionContext.noEncryption()

  private def useTLS():Try[ConnectionContext] = {
    try {
      val ks: KeyStore = KeyStore.getInstance("PKCS12")
      val keystore: InputStream = getClass.getClassLoader.getResourceAsStream(certFile)

      require(keystore != null, "Keystore required!")
      ks.load(keystore, password.toCharArray)

      val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
      keyManagerFactory.init(ks, password.toCharArray)

      val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
      tmf.init(ks)

      val refSslContext: SSLContext = SSLContext.getInstance("TLS")
      val sslContext: SSLContext = SSLContext.getInstance("TLS", refSslContext.getProvider)
      sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
      Success(ConnectionContext.https(sslContext))
    } catch {
      case t:Throwable => logger.error(t.getMessage, t)
        Failure(t)
    }
  }
}
