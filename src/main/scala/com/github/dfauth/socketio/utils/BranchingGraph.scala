package com.github.dfauth.socketio.utils

import akka.NotUsed
import akka.stream.ClosedShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}
import com.github.dfauth.socketio.{FunctionProcessor, PartialFunctionProcessor}
import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.Processor

object BranchingGraph extends LazyLogging {

  def apply[T](src:Source[T, NotUsed], predicate:T => Boolean, sink:Sink[T, NotUsed], sink2:Sink[T, NotUsed]):RunnableGraph[NotUsed] = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import akka.stream.scaladsl.GraphDSL.Implicits._

    val bcast = b.add(Broadcast[T](2))
    src ~> bcast.in
    bcast.out(0) ~> Flow[T].filter(predicate) ~> sink
    bcast.out(1) ~> Flow[T].filterNot(predicate) ~> sink2
    ClosedShape
  })
}

object MergingGraph {

  def apply[T](src:Source[T, NotUsed], src2:Source[T, NotUsed]) = {

    val processor = FunctionProcessor[T]("mergingGraph")
    val sink = Sink.fromSubscriber(processor)
    val internalSrc = Source.fromPublisher(processor)

    val graph = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
      import akka.stream.scaladsl.GraphDSL.Implicits._

      val merge = b.add(Merge[T](2))
      src ~> merge.in(0)
      src2 ~> merge.in(1)
      merge.out ~> sink
      ClosedShape
    })
    (internalSrc, graph)
  }
}

object ShortCircuit {

  def apply[T,U](topSrc:Source[T, NotUsed], topSink:Sink[T, NotUsed], pf: PartialFunction[T,U]):Tuple2[Source[U, NotUsed], RunnableGraph[NotUsed]] = {
    val predicate:T=>Boolean = (t:T) => !pf.isDefinedAt(t)
    apply(topSrc, topSink, predicate, pf)
  }

  def apply[T,U](topSrc:Source[T, NotUsed], topSink:Sink[T, NotUsed], predicate:T => Boolean, pf: PartialFunction[T,U]):Tuple2[Source[U, NotUsed], RunnableGraph[NotUsed]] = {
    val processor:Processor[T,U] = PartialFunctionProcessor(pf, "shortCircuit")
    val internalSink:Sink[T, NotUsed] = Sink.fromSubscriber(processor)
    val graph = BranchingGraph(topSrc, predicate, topSink, internalSink)
    val internalSource = Source.fromPublisher(processor)
    (internalSource, graph)
  }
}
