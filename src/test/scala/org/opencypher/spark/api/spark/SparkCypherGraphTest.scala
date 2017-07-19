package org.opencypher.spark.api.spark

import org.opencypher.spark.SparkCypherTestSuite
import org.opencypher.spark.api.ir.global.TokenRegistry
import org.opencypher.spark.api.record._
import org.opencypher.spark.api.types.CTNode

class SparkCypherGraphTest extends SparkCypherTestSuite {

  implicit val space = SparkGraphSpace.empty(session, TokenRegistry.empty)

  val `:Person` =
    NodeScan.on("p" -> "ID") {
      _.build
       .withImpliedLabel("Person")
       .withOptionalLabel("Swedish" -> "IS_SWEDE")
       .withProperty("name" -> "NAME")
       .withProperty("lucky_number" -> "NUM")
    }
    .from(SparkCypherRecords.create(
      Seq("ID", "IS_SWEDE", "NAME", "NUM"),
      Seq(
        (1, true, "Mats", 23),
        (2, false, "Martin", 42),
        (3, false, "Max", 1337),
        (4, false, "Stefan", 9))
    ))

  val `:Book` =
    NodeScan.on("b" -> "ID") {
      _.build
        .withImpliedLabel("Book")
        .withProperty("title" -> "NAME")
        .withProperty("year" -> "YEAR")
    }
      .from(SparkCypherRecords.create(
        Seq("ID", "NAME", "YEAR"),
        Seq(
          (1, "1984", 1949),
          (2, "Cryptonomicon", 1999),
          (3, "The Eye of the World", 1990),
          (4, "The Circle", 2013))
      ))

  val `:KNOWS` =
    RelationshipScan.on("r" -> "ID") {
      _.from("SRC").to("DST").relType("KNOWS")
       .build
       .withProperty("since" -> "SINCE")
    }
    .from(SparkCypherRecords.create(
      Seq("SRC", "ID", "DST", "SINCE"),
      Seq(
        (1, 1, 2, 2017),
        (1, 2, 3, 2016),
        (1, 3, 4, 2015),
        (2, 4, 3, 2016),
        (2, 5, 4, 2013),
        (3, 6, 4, 2016))
    ))

  test("Construct graph from single node scan") {
    val graph = SparkCypherGraph.create(`:Person`)
    val nodes = graph.nodes("n")
    nodes shouldMatch `:Person`.records
  }

  test("Construct graph from multiple node scans") {
    val graph = SparkCypherGraph.create(`:Person`, `:Book`)
    val nodes = graph.nodes("n")

    nodes shouldMatch `:Person`.records
  }

  test("Construct graph from scans") {
     val graph = SparkCypherGraph.create(`:Person`, `:KNOWS`)

     val nodes = graph.nodes("n")

     nodes shouldMatch `:Person`.records
  }
}
