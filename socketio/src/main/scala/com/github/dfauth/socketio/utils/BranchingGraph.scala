package com.github.dfauth.socketio.utils

import akka.stream.{ClosedShape, Materializer}
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Merge, RunnableGraph, Sink, Source}
import com.github.dfauth.socketio.reactivestreams.{FunctionProcessor, PartialFunctionProcessor, Processors}
import com.typesafe.scalalogging.LazyLogging
import org.reactivestreams.Processor

import scala.reflect.ClassTag

object SplittingGraph extends LazyLogging {

  def apply[T:ClassTag](src:Source[T, Any]):Tuple2[Source[T, Any], Source[T, Any]] = {

    val (sink0, src0) = Processors.sinkToSource[T]
    val (sink1, src1) = Processors.sinkToSource[T]

    apply(src, sink0, sink1)

    (src0, src1)
}

  def split[T:ClassTag](src:Source[T, Any]):BuilderStage[T] = {

    val (sink0, src0) = Processors.sinkToSource[T]
    val (sink1, src1) = Processors.sinkToSource[T]
    val graph:RunnableGraph[Any] = apply(src, sink0, sink1)
    new IntermediateBuilderStage(graph, src0, Seq(src1))
}

def apply[T](src:Source[T, Any], sink:Sink[T, Any], sink2:Sink[T, Any]):RunnableGraph[Any] = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import akka.stream.scaladsl.GraphDSL.Implicits._

    val bcast = b.add(Broadcast[T](2))
    src ~> bcast.in
    bcast.out(0) ~> sink
    bcast.out(1) ~> sink2
    ClosedShape
  })
}

object BranchingGraph extends LazyLogging {

  def apply[T](src:Source[T, Any], predicate:T => Boolean, sink:Sink[T, Any], sink2:Sink[T, Any]):RunnableGraph[Any] = RunnableGraph.fromGraph(GraphDSL.create() { implicit b =>
    import akka.stream.scaladsl.GraphDSL.Implicits._

    val bcast = b.add(Broadcast[T](2))
    src ~> bcast.in
    bcast.out(0) ~> Flow[T].filter(predicate) ~> sink
    bcast.out(1) ~> Flow[T].filterNot(predicate) ~> sink2
    ClosedShape
  })
}

object MergingGraph {

  def apply[T](src:Source[T, Any], src2:Source[T, Any]) = {

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

  def apply[T,U](topSrc:Source[T, Any], topSink:Sink[T, Any], pf: PartialFunction[T,U]):Tuple2[Source[U, Any], RunnableGraph[Any]] = {
    val predicate:T=>Boolean = (t:T) => !pf.isDefinedAt(t)
    apply(topSrc, topSink, predicate, pf)
  }

  def apply[T,U](topSrc:Source[T, Any], topSink:Sink[T, Any], predicate:T => Boolean, pf: PartialFunction[T,U]):Tuple2[Source[U, Any], RunnableGraph[Any]] = {
    val processor:Processor[T,U] = PartialFunctionProcessor(pf, "shortCircuit")
    val internalSink:Sink[T, Any] = Sink.fromSubscriber(processor)
    val graph = BranchingGraph(topSrc, predicate, topSink, internalSink)
    val internalSource = Source.fromPublisher(processor)
    (internalSource, graph)
  }
}

trait BuilderStage[T] {
  protected val graph:RunnableGraph[Any]
  protected val src:Source[T, Any]
  def and(sink: Sink[T, Any]):BuilderStage[T] = to(sink)
  def withSource:FinalBuilderStage[T] = new FinalBuilderStage[T](graph, src)
  def to(sink: Sink[T, Any]) = {
    src.to(sink)
    this
  }
}

class IntermediateBuilderStage[T](override val graph:RunnableGraph[Any], override val src:Source[T, Any], srcList:Seq[Source[T, Any]]) extends BuilderStage[T] {
  override def to(sink: Sink[T, Any]):BuilderStage[T] = {
    src.to(sink)
    srcList match {
      case src1 :: Nil => new FinalBuilderStage[T](graph, src1)
      case src1 :: tail => new IntermediateBuilderStage[T](graph, src1, tail)
    }
  }
}

class FinalBuilderStage[T](override val graph:RunnableGraph[Any], override val src:Source[T, Any]) extends BuilderStage[T] {
  def run(implicit m:Materializer):Source[T, Any] = {
    graph.run()
    src
  }
}