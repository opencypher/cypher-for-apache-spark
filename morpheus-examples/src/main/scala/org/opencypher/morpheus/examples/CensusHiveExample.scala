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
// tag::full-example[]
package org.opencypher.morpheus.examples

import org.apache.spark.sql.SparkSession
import org.opencypher.morpheus.api.io.sql.SqlDataSourceConfig.Hive
import org.opencypher.morpheus.api.{GraphSources, MorpheusSession}
import org.opencypher.morpheus.util.HiveUtils._
import org.opencypher.morpheus.util.{App, CensusDB}
import org.opencypher.okapi.api.graph.Namespace

object CensusHiveExample extends App {

  implicit val resourceFolder: String = "/census"

  // tag::create-session[]
  // Create a Spark and a Morpheus session
  implicit val morpheus: MorpheusSession = MorpheusSession.local(hiveExampleSettings: _*)
  implicit val sparkSession: SparkSession = morpheus.sparkSession
  // end::create-session[]


  // tag::register-sql-source-in-session[]
  // Register a SQL source (for Hive) in the Cypher session
  val graphName = "Census_1901"
  val sqlGraphSource = GraphSources
      .sql(resource("ddl/census.ddl").getFile)
      .withSqlDataSourceConfigs("CENSUS" -> Hive)

  // tag::prepare-sql-database[]
  // Create the data in Hive
  CensusDB.createHiveData(Hive)
  // end::prepare-sql-database[]

  morpheus.registerSource(Namespace("sql"), sqlGraphSource)
  // end::register-sql-source-in-session[]

  // tag::access-registered-graph[]
  // Access the graph via its qualified graph name
  val census = morpheus.catalog.graph("sql." + graphName)
  // end::access-registered-graph[]

  // tag::query-graph[]
  // Run a simple Cypher query
  census.cypher(
    s"""
       |FROM GRAPH sql.$graphName
       |MATCH (n:Person)-[r]->(m)
       |WHERE n.age >= 30
       |RETURN n,r,m
       |ORDER BY n.age
    """.stripMargin)
    .records
    .show
  // end::query-graph[]
}
// end::full-example[]
