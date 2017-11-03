/*
 * Copyright (c) 2016-2017 "Neo4j, Inc." [https://neo4j.com]
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
 */
package org.opencypher.caps.impl.logical

import org.opencypher.caps.api.expr._
import org.opencypher.caps.api.record.{ProjectedExpr, ProjectedField}
import org.opencypher.caps.api.types._
import org.opencypher.caps.impl.util._
import org.opencypher.caps.ir.api.block.{Aggregations, SortItem}
import org.opencypher.caps.ir.api.pattern.{EveryNode, EveryRelationship}
import org.opencypher.caps.ir.api.{IRField, SolvedQueryModel}

// TODO: Homogenize naming
// TODO: Align names with other producers
class LogicalOperatorProducer {

  def planCartesianProduct(lhs: LogicalOperator, rhs: LogicalOperator): CartesianProduct = {
    CartesianProduct(lhs, rhs)(lhs.solved ++ rhs.solved)
  }

  def planValueJoin(lhs: LogicalOperator, rhs: LogicalOperator, predicates: Set[org.opencypher.caps.api.expr.Equals]): ValueJoin = {
    ValueJoin(lhs, rhs, predicates)(predicates.foldLeft(lhs.solved ++ rhs.solved) { case (solved, predicate) => solved.withPredicate(predicate) })
  }

  def planBoundedVarLengthExpand(source: IRField, r: IRField, types: EveryRelationship, target: IRField, lower: Int, upper: Int, sourcePlan: LogicalOperator, targetPlan: LogicalOperator): BoundedVarLengthExpand = {
    val prevSolved = sourcePlan.solved ++ targetPlan.solved

    val solved = types.relTypes.elements.foldLeft(prevSolved.withField(r)) {
      case (acc, next) => acc.withPredicate(HasType(r, next)(CTBoolean))
    }

    BoundedVarLengthExpand(source, r, target, lower, upper, sourcePlan, targetPlan)(solved)
  }

  def planTargetExpand(source: IRField, rel: IRField, target: IRField, sourcePlan: LogicalOperator, targetPlan: LogicalOperator): ExpandTarget = {
    val prevSolved = sourcePlan.solved ++ targetPlan.solved

    val solved = prevSolved.withField(rel)

    ExpandTarget(source, rel, target, sourcePlan, targetPlan)(solved)
  }

  def planSourceExpand(source: IRField, rel: IRField, types: EveryRelationship, target: IRField,
                       sourcePlan: LogicalOperator, targetPlan: LogicalOperator): ExpandSource = {

    val prevSolved = sourcePlan.solved ++ targetPlan.solved

    val solved = types.relTypes.elements.foldLeft(prevSolved.withField(rel)) {
      case (acc, next) => acc.withPredicate(HasType(rel, next)(CTBoolean))
    }

    ExpandSource(source, rel, types, target, sourcePlan, targetPlan)(solved)
  }

  def planExpandInto(source: IRField, rel: IRField, types: EveryRelationship, target: IRField, sourcePlan: LogicalOperator): ExpandInto = {
    val solved = types.relTypes.elements.foldLeft(sourcePlan.solved.withField(rel)) {
      case (acc, next) => acc.withPredicate(HasType(rel, next)(CTBoolean))
    }

    ExpandInto(source, rel, types, target, sourcePlan)(solved)
  }

  def planNodeScan(node: IRField, everyNode: EveryNode, prev: LogicalOperator): NodeScan = {
    val solved = everyNode.labels.elements.foldLeft(prev.solved.withField(node)) {
      case (acc, label) => acc.withPredicate(HasLabel(node, label)(CTBoolean))
    }

    NodeScan(node, everyNode, prev)(solved)
  }

  def planFilter(expr: Expr, prev: LogicalOperator): Filter = {
    Filter(expr, prev)(prev.solved.withPredicate(expr))
  }

  def planDistinct(fields: Set[IRField], prev: LogicalOperator): Distinct = {
    Distinct(fields.map(toVar), prev)(prev.solved)
  }

  def planOptional(nonOptionalPlan: LogicalOperator, optionalPlan: LogicalOperator): Optional = {
    Optional(nonOptionalPlan, optionalPlan)(optionalPlan.solved)
  }

  def aggregate(aggregations: Aggregations[Expr], group: Set[IRField], prev: LogicalOperator): Aggregate = {
    val transformed = aggregations.pairs.collect { case (field, aggregator: Aggregator) => toVar(field) -> aggregator }

    Aggregate(transformed, group.map(toVar), prev)(prev.solved.withFields(aggregations.fields.toSeq: _*))
  }

  def projectField(field: IRField, expr: Expr, prev: LogicalOperator): Project = {
    val projection = ProjectedField(field, expr)

    Project(projection, prev)(prev.solved.withField(field))
  }

  def projectExpr(expr: Expr, prev: LogicalOperator): Project = {
    val projection = ProjectedExpr(expr)

    Project(projection, prev)(prev.solved)
  }

  def planSelect(fields: IndexedSeq[Var], graphs: Set[String] = Set.empty, prev: LogicalOperator): Select = {
    Select(fields, graphs, prev)(prev.solved)
  }

  def planSetSourceGraph(graph: LogicalGraph, prev: LogicalOperator): SetSourceGraph = {
    SetSourceGraph(graph, prev)(prev.solved)
  }

  def planStart(graph: LogicalGraph, fields: Set[Var]): Start = {
    val irFields = fields.map { v => IRField(v.name)(v.cypherType) }
    Start(graph, fields)(SolvedQueryModel[Expr](irFields))
  }

  def planOrderBy(sortItems: Seq[SortItem[Expr]], prev: LogicalOperator): OrderBy = {
    OrderBy(sortItems, prev)(prev.solved)
  }

  def planSkip(expr: Expr, prev: LogicalOperator): Skip = {
    Skip(expr, prev)(prev.solved)
  }

  def planLimit(expr: Expr, prev: LogicalOperator): Limit = {
    Limit(expr, prev)(prev.solved)
  }
}
