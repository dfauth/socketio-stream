import sbt.Keys.libraryDependencies

name := "socketio-stream"

version := "0.1-SNAPSHOT"

scalaVersion := "2.12.6"

lazy val commonSettings = Seq(
  publishArtifact := true
)


val kafkaVersion = "2.4.0"
val akkaVersion = "2.6.1"
val akkaHttpVersion = "10.1.10"
val springVersion = "5.1.8.RELEASE"

val scalactic = "org.scalactic" %% "scalactic" % "3.0.5"
val scalatest = "org.scalatest" %% "scalatest" % "3.0.5" % "test"
val akkaActor = "com.typesafe.akka" %% "akka-actor"   % akkaVersion withSources()
val akkaHttp = "com.typesafe.akka" %% "akka-http"   % akkaHttpVersion
val akkaStream = "com.typesafe.akka" %% "akka-stream" % akkaVersion
val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
val akkaHttpSprayJson = "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion
val kafkaCore = "org.apache.kafka" %% "kafka" % kafkaVersion
val akkaStreamsKafka = "com.typesafe.akka" %% "akka-stream-kafka" % "2.0.1"
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
val reactiveStreams = "org.reactivestreams" % "reactive-streams" % "1.0.3"
val scalaJava8 = "org.scala-lang.modules" %% "scala-java8-compat" % "0.3.0" % Test




val commonScalaDeps = Seq(scalactic, scalatest, akkaHttpSprayJson, scalaLogging, logback)

lazy val authentication = (project in file("authentication"))
  .settings(commonSettings,
    name := "authentication",
  )

lazy val reactivestreams = (project in file("reactivestreams"))
  .settings(commonSettings,
    name := "reactivestreams",
    libraryDependencies ++= Seq(
      scalaLogging,
      scalatest,
      scalactic,
      akkaStream,
      reactiveStreams,
      testNG,
    )
  )

lazy val socketioApi = (project in file("socketio-api"))
  .settings(commonSettings,
    name := "socketio-api",
    libraryDependencies ++= Seq(
      akkaActor,
      akkaStream
    )
  ).dependsOn(authentication)

lazy val socketio = (project in file("socketio"))
  .settings(commonSettings,
    name := "socketio",
    libraryDependencies ++= commonScalaDeps ++ Seq(
      akkaActor,
      akkaHttp,
      akkaStreamTyped,
      reactiveStreams,
      akkaTyped,
      guice,
      socketioClient,
    )
  ).dependsOn(reactivestreams, socketioApi)

lazy val kafkaStream = (project in file("kafka-stream"))
  .settings(commonSettings,
    name := "kafka-stream",
    libraryDependencies ++= commonScalaDeps ++ Seq(
      akkaStreamTyped,
      kafkaCore,
      kafkaReg,
      akkaStreamsKafka,
      kafkaAvroSerializer,
      kafkaClient,
      embeddedKafka,
      jodaTime,
      avro,
    )
  ).dependsOn(socketioApi, reactivestreams, authentication)
  .enablePlugins()

lazy val testStream = (project in file("test-stream"))
  .settings(commonSettings,
    name := "test-stream",
    libraryDependencies ++= commonScalaDeps ++ Seq(
    )
  ).dependsOn(socketio, reactivestreams, authentication)

lazy val socketioStream = (project in file("."))
  .settings(
    publishLocal := {},
  )
  .dependsOn(socketio % "compile->compile;test->test", socketioApi % "compile->compile;test->test", kafkaStream % "compile->compile;test->test", testStream % "compile->compile;test->test", reactivestreams % "compile->compile;test->test", authentication)
  .aggregate(socketio, socketioApi, testStream, kafkaStream, reactivestreams, authentication)
  .enablePlugins(AssemblyPlugin)


