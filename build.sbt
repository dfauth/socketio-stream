name := "socketio-stream"

version := "0.1-SNAPSHOT"

scalaVersion := "2.12.6"

val kafkaVersion = "2.1.1"
val akkaVersion = "2.6.1"
val akkaHttpVersion = "10.1.10"
val springVersion = "5.1.8.RELEASE"

val scalactic = "org.scalactic" %% "scalactic" % "3.0.5"
val scalatest = "org.scalatest" %% "scalatest" % "3.0.5" % "test"
val akkaHttp = "com.typesafe.akka" %% "akka-http"   % akkaHttpVersion
val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
val kafkaCore = "org.apache.kafka" %% "kafka" % kafkaVersion
val akkaStreamsKafka = "com.typesafe.akka" %% "akka-stream-kafka" % "0.14"
val kafkaStreams = "org.apache.kafka" % "kafka-streams" % kafkaVersion withSources()
val kafkaClient = "org.apache.kafka" % "kafka-clients" % kafkaVersion withSources()
val embeddedKafka = "io.github.embeddedkafka" %% "embedded-kafka" % "2.1.1" % Test withSources()
val akkaHttpTestKit = "com.typesafe.akka" %% "akka-http-testkit" % akkaHttpVersion % Test
val akkaSlf4j = "com.typesafe.akka" %% "akka-slf4j" % akkaVersion
val play = "com.typesafe.play" %% "play-json" % "2.6.10"
val springMessaging = "org.springframework" % "spring-messaging" % springVersion
val springWebsocket = "org.springframework" % "spring-websocket" % springVersion
val javaxWebsocket = "javax.websocket" % "javax.websocket-api" % "1.1"
val testNG = "org.testng" % "testng" % "6.14.3" % Test
val tyrus = "org.glassfish.tyrus.bundles" % "tyrus-standalone-client-jdk" % "1.12" % Test
val playSocketIo = "com.lightbend.play" %% "play-socket-io" % "1.0.0-beta-2"
val guice = "com.google.inject" % "guice" % "4.2.2"
val socketioClient = "io.socket" % "socket.io-client" % "1.0.0" % Test
val akkaTyped = "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion
val akkaStreamTyped = "com.typesafe.akka" %% "akka-stream-typed" % akkaVersion
val avro = "org.apache.avro" % "avro" % "1.8.2"
val kafkaReg = "io.confluent" % "kafka-schema-registry-client" % "4.1.1"
val kafkaAvroSerializer = "io.confluent" % "kafka-avro-serializer" % "4.1.1"
val jodaTime = "joda-time" % "joda-time" % "2.10.1"

val commonScalaDeps = Seq(scalactic, scalatest, akkaHttpSprayJson, scalaLogging, logback)


lazy val root = (project in file("."))
  .settings(
    libraryDependencies ++= commonScalaDeps ++ Seq(
      akkaHttp,
      akkaStreamTyped,
      akkaTyped,
      guice,
      kafkaCore,
      kafkaReg,
      akkaStreamsKafka,
      kafkaAvroSerializer,
      kafkaClient,
      embeddedKafka,
      jodaTime,
      avro,
      socketioClient
    )
  )
  .enablePlugins(AssemblyPlugin)


