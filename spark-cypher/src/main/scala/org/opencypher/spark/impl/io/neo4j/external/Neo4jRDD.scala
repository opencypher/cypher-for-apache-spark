/*
 * Copyright (c) 2016-2019 "Neo4j Sweden, AB" [https://neo4j.com]
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Attribution Notice under the terms of the Apache License 2.0
 *
 * This work was created by the collective efforts of the openCypher community.
 * Without limiting the terms of Section 6, any Derivative Work that is not
 * approved by the public consensus process of the openCypher Implementers Group
 * should not be described as “Cypher” (and Cypher® is a registered trademark of
 * Neo4j Inc.) or as "openCypher". Extensions by implementers or prototypes or
 * proposals for change that have been documented or implemented should only be
 * described as "implementation extensions to Cypher" or as "proposed changes to
 * Cypher that are not yet approved by the openCypher community".
 */
package org.opencypher.spark.impl.io.neo4j.external

import org.apache.spark.rdd.RDD
import org.apache.spark.sql.Row
import org.apache.spark.{Partition, SparkContext, TaskContext}
import org.opencypher.okapi.neo4j.io.Neo4jConfig

private class Neo4jRDD(
    sc: SparkContext,
    val query: String,
    val neo4jConfig: Neo4jConfig,
    val parameters: Map[String, Any] = Map.empty,
    partitions: Partitions = Partitions())
    extends RDD[Row](sc, Nil) {

  override def compute(partition: Partition, context: TaskContext): Iterator[Row] = {

    val neo4jPartition: Neo4jPartition = partition.asInstanceOf[Neo4jPartition]

    val pageQuery = s"$query SKIP $$_skip LIMIT $$_limit"

    Executor.execute(neo4jConfig, pageQuery, parameters ++ neo4jPartition.window).sparkRows
  }

  override protected def getPartitions: Array[Partition] = {
    val p = partitions.effective()
    Range(0, p.partitions.toInt).map(idx => new Neo4jPartition(idx, p.skip(idx), p.limit(idx))).toArray
  }

  override def toString(): String = s"Neo4jRDD partitions $partitions $query using $parameters"
}
