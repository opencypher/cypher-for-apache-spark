/*
 * Copyright (c) 2016-2018 "Neo4j Sweden, AB" [https://neo4j.com]
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
package org.opencypher.sql.ddl

import org.opencypher.okapi.api.types.CypherType
import org.opencypher.okapi.trees.AbstractTreeNode
import org.opencypher.sql.ddl.GraphDdlAst._

object GraphDdlAst {
  type Property = (String, CypherType)

  type EntityDefinition = (String, Map[String, CypherType])

  type KeyDefinition = (String, Set[String])

  type ColumnIdentifier = List[String]

  type PropertyToColumnMappingDefinition = Map[String, String]

  type LabelCombination = Set[String]
}

abstract class GraphDdlAst extends AbstractTreeNode[GraphDdlAst]

case class DdlDefinition(
  setSchema: Option[SetSchemaDefinition] = None,
  labelDefinitions: List[LabelDefinition] = Nil,
  schemaDefinitions: List[(String, SchemaDefinition)] = List.empty,
  graphDefinitions: List[GraphDefinition] = Nil
) extends GraphDdlAst

case class SetSchemaDefinition(
  databaseConnectionName: String,
  databaseSchemaName: Option[String] = None
) extends GraphDdlAst

case class LabelDefinition(
  name: String,
  properties: Map[String, CypherType] = Map.empty,
  maybeKeyDefinition: Option[KeyDefinition] = None
) extends GraphDdlAst

case class SchemaDefinition(
  localLabelDefinitions: List[LabelDefinition] = List.empty,
  nodeDefinitions: Set[Set[String]] = Set.empty,
  relDefinitions: Set[String] = Set.empty,
  schemaPatternDefinitions: Set[SchemaPatternDefinition] = Set.empty
) extends GraphDdlAst

case class GraphDefinition(
  name: String,
  maybeSchemaName: Option[String] = None,
  localSchemaDefinition: SchemaDefinition = SchemaDefinition(),
  nodeMappings: List[NodeMappingDefinition] = List.empty,
  relationshipMappings: List[RelationshipMappingDefinition] = List.empty
) extends GraphDdlAst

case class CardinalityConstraint(from: Int, to: Option[Int])

case class SchemaPatternDefinition(
  sourceLabelCombinations: Set[LabelCombination],
  sourceCardinality: CardinalityConstraint = CardinalityConstraint(0, None),
  relTypes: Set[String],
  targetCardinality: CardinalityConstraint = CardinalityConstraint(0, None),
  targetLabelCombinations: Set[LabelCombination]
) extends GraphDdlAst

trait ElementToViewDefinition {
  def maybePropertyMapping: Option[PropertyToColumnMappingDefinition]
}

case class NodeToViewDefinition (
  viewName: String,
  override val maybePropertyMapping: Option[PropertyToColumnMappingDefinition] = None
) extends GraphDdlAst with ElementToViewDefinition

case class NodeMappingDefinition(
  labelNames: Set[String],
  nodeToViewDefinitions: List[NodeToViewDefinition] = List.empty
) extends GraphDdlAst

case class ViewDefinition(name: String, alias: String) extends GraphDdlAst

case class JoinOnDefinition(joinPredicates: List[(ColumnIdentifier, ColumnIdentifier)]) extends GraphDdlAst

case class LabelToViewDefinition(
  labelSet: Set[String],
  viewDefinition: ViewDefinition,
  joinOn: JoinOnDefinition
) extends GraphDdlAst

case class RelationshipToViewDefinition(
  viewDefinition: ViewDefinition,
  override val maybePropertyMapping: Option[PropertyToColumnMappingDefinition] = None,
  startNodeToViewDefinition: LabelToViewDefinition,
  endNodeToViewDefinition: LabelToViewDefinition
) extends GraphDdlAst with ElementToViewDefinition

case class RelationshipMappingDefinition(
  relType: String,
  relationshipToViewDefinitions: List[RelationshipToViewDefinition]
) extends GraphDdlAst