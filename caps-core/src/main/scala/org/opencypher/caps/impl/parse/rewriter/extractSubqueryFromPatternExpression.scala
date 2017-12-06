package org.opencypher.caps.impl.parse.rewriter

import org.neo4j.cypher.internal.frontend.v3_3.ast.rewriters.{nameMatchPatternElements, normalizeMatchPredicates}
import org.neo4j.cypher.internal.frontend.v3_3.ast.{ASTNode, _}
import org.neo4j.cypher.internal.frontend.v3_3.helpers.{FreshIdNameGenerator, UnNamedNameGenerator}
import org.neo4j.cypher.internal.frontend.v3_3.{CypherException, InputPosition, Rewriter, SemanticCheck, SemanticCheckResult, topDown}

case class extractSubqueryFromPatternExpression(mkException: (String, InputPosition) => CypherException) extends Rewriter {

  def apply(that: AnyRef): AnyRef = instance.apply(that)

  private val rewriter = Rewriter.lift {
    case m @ Match(_, _, _, Some(w @ Where(expr))) =>
      m.copy(where = Some(Where(expr.endoRewrite(whereRewriter))(w.position)))(m.position)
  }

  private def whereRewriter: Rewriter = Rewriter.lift {
    /**
      * WHERE (a)-[:R]->({foo:true})-->()... AND a.age > 20
      *
      * to
      *
      * WHERE EXISTS (
      *   MATCH (a)-[e0]->(v0)-[e1]->(v1)...
      *   WHERE e0:R AND v0.foo = true
      *   RETURN a, true
      * ) AND a.age > 20
      */
    case p@PatternExpression(relationshipsPattern) =>
      val patternPosition: InputPosition = p.position
      val newPattern = Pattern(Seq(EveryPath(relationshipsPattern.element)))(patternPosition)

      val joinVariables = relationshipsPattern.element.treeFold(Seq.empty[Variable]) {
        case NodePattern(Some(v), _, _) => (acc) => (acc :+ v, None)
        case RelationshipPattern(Some(v), _, _, _, _, _) => (acc) => (acc :+ v, None)
      }

      val returnItems = joinVariables.map(v => AliasedReturnItem(v, v)(v.position))

      val trueVariable = Variable(UnNamedNameGenerator.name(p.position))(p.position)
      val returnItemsWithTrue = returnItems :+ AliasedReturnItem(True()(trueVariable.position), trueVariable)(trueVariable.position)

      Exists(
        SingleQuery(
          Seq(
            Match(optional = false, newPattern, Seq.empty, None)(patternPosition)
                .endoRewrite(nameMatchPatternElements)
                .endoRewrite(normalizeMatchPredicates)
            ,
            Return(ReturnItems(includeExisting = false, returnItemsWithTrue)(patternPosition), None)(patternPosition)
          )
        )(patternPosition)
      )(patternPosition)

    case a@And(lhs, rhs) =>
      And(lhs.endoRewrite(whereRewriter), rhs.endoRewrite(whereRewriter))(a.position)

    case o@Or(lhs, rhs) => Or(lhs.endoRewrite(whereRewriter), rhs.endoRewrite(whereRewriter))(o.position)

    case n@Not(e) => Not(e.endoRewrite(whereRewriter))(n.position)
  }

  private val instance = topDown(rewriter, _.isInstanceOf[Expression])
}

case class Exists(node: ASTNode)(val position: InputPosition) extends Expression {
  override def semanticCheck(ctx: Expression.SemanticContext): SemanticCheck = SemanticCheckResult.success
}
