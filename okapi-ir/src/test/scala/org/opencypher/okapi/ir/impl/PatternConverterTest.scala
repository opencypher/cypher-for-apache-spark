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
package org.opencypher.okapi.ir.impl

import org.opencypher.okapi.api.types.{CTNode, CTRelationship, CypherType}
import org.opencypher.okapi.api.types._
import org.opencypher.okapi.ir.api.IRField
import org.opencypher.okapi.ir.api.expr._
import org.opencypher.okapi.ir.api.pattern._
import org.opencypher.okapi.ir.impl.util.VarConverters.toField
import org.opencypher.v9_0.parser.{Expressions, Patterns}
import org.opencypher.v9_0.util.InputPosition.NONE
import org.opencypher.v9_0.util.{InputPosition, SyntaxException}
import org.opencypher.v9_0.{expressions => ast}
import org.parboiled.scala.{EOI, Parser, Rule1}

import scala.language.implicitConversions

class PatternConverterTest extends IrTestSuite {

  test("simple node pattern") {
    val pattern = parse("(x)")

    convert(pattern) should equal(
      Pattern.empty.withElement('x -> CTNode.empty)
    )
  }

  it("converts element properties") {
    val pattern = parse("(a:A {name:'Hans'})-[rel:KNOWS {since:2007}]->(a)")
    val a: IRField = 'a -> CTNode.empty("A")
    val rel: IRField = 'rel -> CTRelationship.empty("KNOWS")

    convert(pattern).properties should equal(
      Map(
        a -> MapExpression(Map("name" -> StringLit("Hans"))),
        rel -> MapExpression(Map("since" -> IntegerLit(2007)))
      )
    )
  }

  test("simple rel pattern") {
    val pattern = parse("(x)-[r]->(b)")

    convert(pattern) should equal(
      Pattern.empty
        .withElement('x -> CTNode.empty)
        .withElement('b -> CTNode.empty)
        .withElement('r -> CTRelationship.empty)
        .withConnection('r, DirectedRelationship('x, 'b))
    )
  }

  test("larger pattern") {
    val pattern = parse("(x)-[r1]->(y)-[r2]->(z)")

    convert(pattern) should equal(
      Pattern.empty
        .withElement('x -> CTNode.empty)
        .withElement('y -> CTNode.empty)
        .withElement('z -> CTNode.empty)
        .withElement('r1 -> CTRelationship.empty)
        .withElement('r2 -> CTRelationship.empty)
        .withConnection('r1, DirectedRelationship('x, 'y))
        .withConnection('r2, DirectedRelationship('y, 'z))
    )
  }

  test("disconnected pattern") {
    val pattern = parse("(x), (y)-[r]->(z), (foo)")

    convert(pattern) should equal(
      Pattern.empty
        .withElement('x -> CTNode.empty)
        .withElement('y -> CTNode.empty)
        .withElement('z -> CTNode.empty)
        .withElement('foo -> CTNode.empty)
        .withElement('r -> CTRelationship.empty)
        .withConnection('r, DirectedRelationship('y, 'z))
    )
  }

  test("get predicates from undirected pattern") {
    val pattern = parse("(x)-[r]-(y)")

    convert(pattern) should equal(
      Pattern.empty
        .withElement('x -> CTNode.empty)
        .withElement('y -> CTNode.empty)
        .withElement('r -> CTRelationship.empty)
        .withConnection('r, UndirectedRelationship('y, 'x))
    )
  }

  test("get labels") {
    val pattern = parse("(x:Person), (y:Dog:Person)")

    convert(pattern) should equal(
      Pattern.empty
        .withElement('x -> CTNode.empty("Person"))
        .withElement('y -> CTNode.empty("Person", "Dog"))
    )
  }

  test("get rel type") {
    val pattern = parse("(x)-[r:KNOWS | LOVES]->(y)")

    convert(pattern) should equal(
      Pattern.empty
        .withElement('x -> CTNode.empty)
        .withElement('y -> CTNode.empty)
        .withElement('r -> CTRelationship.empty("KNOWS", "LOVES"))
        .withConnection('r, DirectedRelationship('x, 'y))
    )
  }

  test("reads type from knownTypes") {
    val pattern = parse("(x)-[r]->(y:Person)-[newR:IN]->(z)")

    val knownTypes: Map[ast.Expression, CypherType] = Map(
      ast.Variable("x")(NONE) -> CTNode.empty("Person"),
      ast.Variable("z")(NONE) -> CTNode.empty("Customer"),
      ast.Variable("r")(NONE) -> CTRelationship.empty("FOO")
    )

    val x: IRField = 'x -> CTNode.empty("Person")
    val y: IRField = 'y -> CTNode.empty("Person")
    val z: IRField = 'z -> CTNode.empty("Customer")
    val r: IRField = 'r -> CTRelationship.empty("FOO")
    val newR: IRField = 'newR -> CTRelationship.empty("IN")

    convert(pattern, knownTypes) should equal(
      Pattern.empty
        .withElement(x)
        .withElement(y)
        .withElement(z)
        .withElement(r)
        .withElement(newR)
        .withConnection(r, DirectedRelationship(x, y))
        .withConnection(newR, DirectedRelationship(y, z))
    )
  }

  describe("Conversion of nodes with base element") {
    it("can convert base nodes") {
      val pattern = parse("(y), (x COPY OF y)")

      val knownTypes: Map[ast.Expression, CypherType] = Map(
        ast.Variable("y")(NONE) -> CTNode.empty("Person")
      )

      val x: IRField = 'x -> CTNode.empty("Person")
      val y: IRField = 'y -> CTNode.empty("Person")

      convert(pattern, knownTypes) should equal(
        Pattern.empty
          .withElement(x)
          .withElement(y)
          .withBaseField(x, Some(y))
      )
    }

    it("can convert base nodes and add a label") {
      val pattern = parse("(x), (y COPY OF x:Employee)")

      val knownTypes: Map[ast.Expression, CypherType] = Map(
        ast.Variable("x")(NONE) -> CTNode.empty("Person")
      )

      val x: IRField = 'x -> CTNode.empty("Person")
      val y: IRField = 'y -> CTNode.empty("Person", "Employee")

      convert(pattern, knownTypes) should equal(
        Pattern.empty
          .withElement(x)
          .withElement(y)
          .withBaseField(y, Some(x))
      )
    }

    it("can convert base relationships") {
      val pattern = parse("(x)-[r]->(y), (x)-[r2 COPY OF r]->(y)")

      val knownTypes: Map[ast.Expression, CypherType] = Map(
        ast.Variable("x")(NONE) -> CTNode.empty("Person"),
        ast.Variable("y")(NONE) -> CTNode.empty("Customer"),
        ast.Variable("r")(NONE) -> CTRelationship.empty("FOO")
      )

      val x: IRField = 'x -> CTNode.empty("Person")
      val y: IRField = 'y -> CTNode.empty("Person")
      val r: IRField = 'r -> CTRelationship.empty("FOO")
      val r2: IRField = 'r2 -> CTRelationship.empty("FOO")

      convert(pattern, knownTypes) should equal(
        Pattern.empty
          .withElement(x)
          .withElement(y)
          .withElement(r)
          .withElement(r2)
          .withConnection(r, DirectedRelationship(x, y))
          .withConnection(r2, DirectedRelationship(x, y))
          .withBaseField(r2, Some(r))
      )
    }

    it("can convert base relationships with new type") {
      val pattern = parse("(x)-[r]->(y), (x)-[r2 COPY OF r:BAR]->(y)")

      val knownTypes: Map[ast.Expression, CypherType] = Map(
        ast.Variable("x")(NONE) -> CTNode.empty("Person"),
        ast.Variable("y")(NONE) -> CTNode.empty("Customer"),
        ast.Variable("r")(NONE) -> CTRelationship.empty("FOO")
      )

      val x: IRField = 'x -> CTNode.empty("Person")
      val y: IRField = 'y -> CTNode.empty("Person")
      val r: IRField = 'r -> CTRelationship.empty("FOO")
      val r2: IRField = 'r2 -> CTRelationship.empty("BAR")

      convert(pattern, knownTypes) should equal(
        Pattern.empty
          .withElement(x)
          .withElement(y)
          .withElement(r)
          .withElement(r2)
          .withConnection(r, DirectedRelationship(x, y))
          .withConnection(r2, DirectedRelationship(x, y))
          .withBaseField(r2, Some(r))
      )
    }
  }
  val converter = new PatternConverter(IRBuilderHelper.emptyIRBuilderContext)

  def convert(p: ast.Pattern, knownTypes: Map[ast.Expression, CypherType] = Map.empty): Pattern =
    converter.convert(p, knownTypes, testQualifiedGraphName)

  def parse(exprText: String): ast.Pattern = PatternParser.parse(exprText, None)

  object PatternParser extends Parser with Patterns with Expressions {

    def SinglePattern: Rule1[Seq[ast.Pattern]] = rule {
      oneOrMore(Pattern) ~~ EOI.label("end of input")
    }

    @throws(classOf[SyntaxException])
    def parse(exprText: String, offset: Option[InputPosition]): ast.Pattern =
      parseOrThrow(exprText, offset, SinglePattern)
  }

}
