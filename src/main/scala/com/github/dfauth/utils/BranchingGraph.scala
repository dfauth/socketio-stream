package com.github.dfauth.utils

import akka.NotUsed
import akka.stream.ClosedShape
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}
import com.github.dfauth.socketio.{FunctionProcessor, PartialFunctionProcessor}
import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.Processor

object BranchingGraph extends LazyLogging {

  def log[T](msg:String):Flow[T, T, NotUsed] = Flow.fromFunction(i => {
    logger.info(s"${msg} payload: ${i}")
    i
  })

  def apply[T](src:Source[T, NotUsed], predicate:T => Boolean, sink:Sink[T, NotUsed], sink2:Sink[T, NotUsed]):RunnableGraph[NotUsed] = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import akka.stream.scaladsl.GraphDSL.Implicits._

    val bcast = b.add(Broadcast[T](2))
    src ~> bcast.in
    bcast.out(0) ~> Flow[T].filter(predicate).via(log("WOOZ upper")) ~> sink
    bcast.out(1) ~> Flow[T].filterNot(predicate).via(log("WOOZ lower")) ~> sink2
    ClosedShape
  })
}

object MergingGraph {

  def apply[T](src:Source[T, NotUsed], src2:Source[T, NotUsed], sink:Sink[T, NotUsed]) = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import akka.stream.scaladsl.GraphDSL.Implicits._

    val merge = b.add(Merge[T](2))
    src ~> merge.in(0)
    src2 ~> merge.in(1)
    merge.out ~> sink
    ClosedShape
  })
}

object ShortCircuit {
  def apply[T,U](topSrc:Source[T, NotUsed], topSink:Sink[T, NotUsed], pf: PartialFunction[T,U], invert:Boolean = false):Tuple2[Source[U, NotUsed], RunnableGraph[NotUsed]] = {
    val processor:Processor[T,U] = PartialFunctionProcessor(pf, "shortCircuit")
    val internalSink:Sink[T, NotUsed] = Sink.fromSubscriber(processor)
    val logic:T=>Boolean = if(invert){
      (t:T) => !pf.isDefinedAt(t)
    } else {
      pf.isDefinedAt
    }
    val graph = BranchingGraph(topSrc, logic, topSink, internalSink)
    val internalSource = Source.fromPublisher(processor)
    (internalSource, graph)
  }
//  def apply[T,U](src:Source[T, NotUsed], sink:Sink[T, NotUsed], pf: PartialFunction[T,U], src2:Source[U, NotUsed], sink2:Sink[U, NotUsed]) = {
//    val processor:Processor[T,U] = new PartialFunctionProcessor(pf)
//    val internalSource:Source[U, NotUsed] = Source.fromPublisher(processor)
//    val internalSink:Sink[T, NotUsed] = Sink.fromSubscriber(processor)
//    val top = BranchingGraph(src, pf.isDefinedAt, sink, internalSink)
//    val bottom = MergingGraph(src2, internalSource, sink2)
//    (Flow.fromSinkAndSource(sink, src), Flow.fromSinkAndSource(sink2, src2))
//  }
}
