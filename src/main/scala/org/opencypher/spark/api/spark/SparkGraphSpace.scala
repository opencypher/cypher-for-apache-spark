package org.opencypher.spark.api.spark

import org.opencypher.spark.api.graph.GraphSpace
import org.opencypher.spark.impl.spark.{SparkGraphLoading, SparkGraphSpaceImpl}

trait SparkGraphSpace extends GraphSpace {
  override type Graph = SparkCypherGraph

  def session: SparkCypherSession
}

object SparkGraphSpace extends SparkGraphLoading with Serializable {
  def createEmpty(implicit session: SparkCypherSession): SparkGraphSpace =
    new SparkGraphSpaceImpl
}
