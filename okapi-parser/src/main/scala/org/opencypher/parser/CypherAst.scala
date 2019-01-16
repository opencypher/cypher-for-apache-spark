/**
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
package org.opencypher.parser

import cats.data.NonEmptyList
import org.opencypher.okapi.trees.AbstractTreeNode

abstract class CypherAst extends AbstractTreeNode[CypherAst]

case class Cypher(statement: Statement) extends CypherAst

sealed trait Query extends Statement

sealed trait RegularQuery extends Query

case class SingleQuery(
  clauses: NonEmptyList[Clause]
) extends RegularQuery

case class Union(all: Boolean, left: RegularQuery, right: SingleQuery) extends RegularQuery

sealed trait ProcedureInvocation

case class StandaloneCall(procedureInvocation: ProcedureInvocation, yieldItems: List[YieldItem]) extends Query

sealed trait Expression extends ReturnItem

case class OrExpression(expressions: NonEmptyList[Expression]) extends Expression

case class XorExpression(expressions: NonEmptyList[Expression]) extends Expression

case class AndExpression(expressions: NonEmptyList[Expression]) extends Expression

case class NotExpression(expression: Expression) extends Expression

case class EqualExpression(left: Expression, right: Expression) extends Expression

case class LessThanExpression(left: Expression, right: Expression) extends Expression

case class LessThanOrEqualExpression(left: Expression, right: Expression) extends Expression

case class AddExpression(left: Expression, right: Expression) extends Expression

case class SubtractExpression(left: Expression, right: Expression) extends Expression

case class MultiplyExpression(left: Expression, right: Expression) extends Expression

case class DivideExpression(left: Expression, right: Expression) extends Expression

case class ModuloExpression(left: Expression, right: Expression) extends Expression

case class PowerOfExpression(base: Expression, exponent: Expression) extends Expression

case class UnarySubtractExpression(expression: Expression) extends Expression

case class StringListNullOperatorExpression(
  propertyOrLabelsExpression: Expression,
  operatorExpressions: NonEmptyList[OperatorExpression]
) extends Expression

sealed trait OperatorExpression extends Expression

sealed trait StringOperatorExpression extends OperatorExpression {
  def expression: Expression
}

case class StartsWith(expression: Expression) extends StringOperatorExpression
case class EndsWith(expression: Expression) extends StringOperatorExpression
case class Contains(expression: Expression) extends StringOperatorExpression

trait ListOperatorExpression extends OperatorExpression

case class In(expression: Expression) extends ListOperatorExpression
case class SingleElementListOperatorExpression(expression: Expression) extends ListOperatorExpression

case class RangeListOperatorExpression(maybeFrom: Option[Expression], maybeTo: Option[Expression])
  extends ListOperatorExpression

sealed trait NullOperatorExpression extends OperatorExpression

case object IsNull extends NullOperatorExpression

case object IsNotNull extends NullOperatorExpression

sealed trait Properties extends CypherAst

case class Alias(expr: Expression, as: Variable) extends ReturnItem

case class SetClause(setItems: NonEmptyList[SetItem]) extends UpdatingClause

case class RelationshipDetail(
  maybeVariable: Option[Variable],
  relationshipTypes: List[RelTypeName],
  maybeRangeLiteral: Option[RangeLiteral],
  maybeProperties: Option[Properties]
) extends CypherAst

sealed trait Atom extends Expression

case class Return(distinct: Boolean, returnBody: ReturnBody) extends Clause

case class CaseAlternatives(whenExpr: Expression, thenExpr: Expression) extends CypherAst

sealed trait RelationshipPattern extends CypherAst {

  def relationshipDetail: RelationshipDetail

}

case class LeftToRight(relationshipDetail: RelationshipDetail) extends RelationshipPattern

case class RightToLeft(relationshipDetail: RelationshipDetail) extends RelationshipPattern

case class Undirected(relationshipDetail: RelationshipDetail) extends RelationshipPattern

case class With(distinct: Boolean, returnBody: ReturnBody, maybeWhere: Option[Where]) extends Clause

case class PatternElement(nodePattern: NodePattern, patternElementChain: List[PatternElementChain]) extends CypherAst

case class PropertyExpression(
  atom: Atom,
  propertyLookups: NonEmptyList[PropertyLookup]
) extends RemoveItem

sealed trait Parameter extends Properties with Atom

case class IndexParameter(index: Long) extends Parameter

case class SymbolicName(value: String) extends Parameter with FunctionName

case class Pattern(patternParts: NonEmptyList[PatternPart]) extends CypherAst

case class PropertyOrLabelsExpression(
  atom: Atom,
  propertyLookups: List[PropertyLookup],
  maybeNodeLabels: List[NodeLabel]
) extends Expression

case class PatternElementChain(relationshipPattern: RelationshipPattern, nodePattern: NodePattern) extends CypherAst

case class NodePattern(
  maybeVariable: Option[Variable],
  nodeLabels: List[NodeLabel],
  maybeProperties: Option[Properties]
) extends CypherAst

case class Variable(name: String) extends Atom

case class NodeLabel(nodeLabel: String) extends CypherAst

case class SortItem(expression: Expression, maybeSortOrder: Option[SortOrder]) extends CypherAst

sealed trait SortOrder

case object Ascending extends SortOrder

case object Descending extends SortOrder

case class Limit(expression: Expression) extends CypherAst

case class ParenthesizedExpression(expression: Expression) extends Atom

case class ReturnItems(star: Boolean, returnItems: List[ReturnItem]) extends CypherAst

case class Remove(removeItems: NonEmptyList[RemoveItem]) extends UpdatingClause

sealed trait Literal extends Atom

sealed trait FunctionName extends CypherAst

case object Exists extends FunctionName

sealed trait PropertyLookup extends CypherAst {
  def value: String
}

case class StringLiteral(value: String) extends Literal

case object NullLiteral extends Literal

case class BooleanLiteral(value: Boolean) extends Literal

sealed trait NumberLiteral extends Literal

case class IntegerLiteral(value: Long) extends NumberLiteral

case class DoubleLiteral(value: Double) extends NumberLiteral

case class FilterExpression(idInColl: IdInColl, maybeWhere: Option[Where]) extends Atom

/** Order switched between `expression` and `maybeWhereExpression`
  * to avoid children allocation problems between them.
  */
case class PatternComprehension(
  maybeVariable: Option[Variable],
  relationshipsPattern: RelationshipsPattern,
  expression: Expression,
  maybeWhereExpression: Option[Expression]
) extends Atom

sealed trait UpdatingStartClause extends Clause

case class YieldItem(maybeProcedureResultField: Option[ProcedureResultField], variable: Variable) extends CypherAst

sealed trait SetItem extends CypherAst

case class SetProperty(expression: PropertyExpression, value: Expression) extends SetItem

case class SetVariable(variable: Variable, value: Expression) extends SetItem

case class SetAdditionalItem(variable: Variable, value: Expression) extends SetItem

case class SetLabels(variable: Variable, nodeLabels: NonEmptyList[NodeLabel]) extends SetItem

case class ProcedureName(namespace: Namespace, symbolicName: SymbolicName) extends CypherAst

sealed trait RemoveItem extends CypherAst

case class RemoveNodeVariable(variable: Variable, nodeLabels: NonEmptyList[NodeLabel]) extends RemoveItem

case class ImplicitProcedureInvocation(procedureName: ProcedureName) extends ProcedureInvocation

case class Where(expression: Expression) extends CypherAst

sealed trait ReturnItem extends CypherAst

case class Delete(detach: Boolean, expressions: NonEmptyList[Expression]) extends UpdatingClause

case class RangeLiteral(maybeFrom: Option[IntegerLiteral], maybeTo: Option[IntegerLiteral]) extends CypherAst

case class RelationshipsPattern(
  nodePattern: NodePattern,
  patternElementChains: NonEmptyList[PatternElementChain]
) extends Atom

case class PatternPart(maybeVariable: Option[Variable], element: PatternElement) extends CypherAst

sealed trait Statement extends CypherAst

sealed trait Clause extends CypherAst

sealed trait ReadingClause extends Clause

case class ProcedureResultField(symbolicName: SymbolicName) extends CypherAst

case class RelTypeName(relTypeName: String) extends CypherAst

case class Namespace(symbolicNames: List[SymbolicName]) extends CypherAst

case class ListLiteral(listLiterals: List[Expression]) extends Literal

case class MapLiteral(properties: List[(PropertyKeyName, Expression)]) extends Properties with Literal

case class InQueryCall(
  explicitProcedureInvocation: ExplicitProcedureInvocation,
  yieldItems: List[YieldItem]
) extends ReadingClause

case class FunctionInvocation(
  functionName: FunctionName,
  distinct: Boolean,
  expressions: List[Expression]
) extends Atom

case class ReturnBody(
  returnItems: ReturnItems,
  maybeOrderBy: Option[OrderBy],
  maybeSkip: Option[Skip],
  maybeLimit: Option[Limit]
) extends CypherAst

case class OrderBy(sortItems: NonEmptyList[SortItem]) extends CypherAst

case class Unwind(expression: Expression, variable: Variable) extends ReadingClause

case class IdInColl(variable: Variable, expression: Expression) extends CypherAst

case class CaseExpression(
  maybeCaseExpression: Option[Expression],
  caseAlternatives: NonEmptyList[CaseAlternatives],
  maybeDefault: Option[Expression]
) extends Atom

case class Match(optional: Boolean, pattern: Pattern, maybeWhere: Option[Where]) extends ReadingClause

case class ExplicitProcedureInvocation(
  procedureName: ProcedureName,
  expressions: List[Expression]
) extends ProcedureInvocation

case class Create(pattern: Pattern) extends UpdatingStartClause with UpdatingClause

sealed trait UpdatingClause extends Clause

case class Skip(expression: Expression) extends CypherAst

case class ListComprehension(
  filterExpression: FilterExpression,
  maybeExpression: Option[Expression]
) extends Atom

case class Merge(
  patternPart: PatternPart,
  mergeActions: List[MergeAction]
) extends UpdatingStartClause with UpdatingClause

sealed trait MergeAction extends CypherAst

case class OnMerge(set: SetClause) extends MergeAction

case class OnCreate(set: SetClause)  extends MergeAction

case class PropertyKeyName(value: String) extends PropertyLookup

case object CountStar extends Atom

case class FilterAll(filterExpression: FilterExpression) extends Atom

case class FilterAny(filterExpression: FilterExpression) extends Atom

case class FilterNone(filterExpression: FilterExpression) extends Atom

case class FilterSingle(filterExpression: FilterExpression) extends Atom
