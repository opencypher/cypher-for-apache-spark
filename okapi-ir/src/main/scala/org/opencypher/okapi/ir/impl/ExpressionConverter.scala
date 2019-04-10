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

import org.opencypher.okapi.api.types._
import org.opencypher.okapi.api.value.CypherValue.CypherInteger
import org.opencypher.okapi.impl.exception.{IllegalArgumentException, NotImplementedException}
import org.opencypher.okapi.ir.api._
import org.opencypher.okapi.ir.api.expr._
import org.opencypher.okapi.ir.impl.BigDecimalSignatures.{Addition, Division, Multiplication}
import org.opencypher.okapi.ir.impl.SignatureTyping._
import org.opencypher.okapi.ir.impl.parse.functions.FunctionExtensions
import org.opencypher.okapi.ir.impl.parse.{functions => f}
import org.opencypher.okapi.ir.impl.typer.SignatureConverter.Signature
import org.opencypher.okapi.ir.impl.typer.{InvalidArgument, InvalidContainerAccess, MissingParameter, NoSuitableSignatureForExpr, SignatureConverter, UnTypedExpr, WrongNumberOfArguments}
import org.opencypher.v9_0.expressions.{OperatorExpression, RegexMatch, TypeSignatures, functions}
import org.opencypher.v9_0.{expressions => ast}

import scala.language.implicitConversions

object AddType {

  private implicit class RichCTList(val left: CTList) extends AnyVal {
    def listConcatJoin(right: CypherType): CypherType = (left, right) match {
      case (CTList(lInner), CTList(rInner)) => CTList(lInner join rInner)
      case (CTList(lInner), _) => CTList(lInner join right)
    }
  }

  def apply(lhs: CypherType, rhs: CypherType): Option[CypherType] = {
    val addSignature: Signature = args => {
      val result = args.map(_.material) match {
        case Seq(CTVoid, _) | Seq(_, CTVoid) => Some(CTNull)
        case Seq(left: CTList, _) => Some(left listConcatJoin rhs)
        case Seq(_, right: CTList) => Some(right listConcatJoin lhs)
        case Seq(CTString, _) if rhs.subTypeOf(CTNumber) => Some(CTString)
        case Seq(_, CTString) if lhs.subTypeOf(CTNumber) => Some(CTString)
        case Seq(CTString, CTString) => Some(CTString)
        case Seq(CTDuration, CTDuration) => Some(CTDuration)
        case Seq(CTLocalDateTime, CTDuration) => Some(CTLocalDateTime)
        case Seq(CTDuration, CTLocalDateTime) => Some(CTLocalDateTime)
        case Seq(CTDate, CTDuration) => Some(CTDate)
        case Seq(CTDuration, CTDate) => Some(CTDate)
        case Seq(CTInteger, CTInteger) => Some(CTInteger)
        case Seq(CTFloat, CTInteger) => Some(CTFloat)
        case Seq(CTInteger, CTFloat) => Some(CTFloat)
        case Seq(CTFloat, CTFloat) => Some(CTFloat)
        case _ => None
      }

      result.map(_.asNullableAs(rhs join lhs))
    }
    val sigs = Set(addSignature, BigDecimalSignatures.arithmeticSignature(Addition))

    returnTypeFor(Seq.empty, Seq(lhs, rhs), sigs)
  }

}

final class ExpressionConverter(context: IRBuilderContext) {

  private def schema = context.workingGraph.schema

  private def parameterType(p: ast.Parameter): CypherType = {
    context.parameters.get(p.name) match {
      case None => throw MissingParameter(p.name)
      case Some(param) => param.cypherType
    }
  }

  private def extractLong(expr: Expr): Long = {
    expr match {
      case param: Param => context.parameters(param.name) match {
        case CypherInteger(i) => i
        case other => throw IllegalArgumentException("a CypherInteger value", other)
      }
      case l: IntegerLit => l.v
      case _ => throw IllegalArgumentException("a literal value", expr)
    }
  }

  def convert(e: ast.Expression): Expr = {

    lazy val child0: Expr = convert(e.arguments.head)

    lazy val child1: Expr = convert(e.arguments(1))

    lazy val child2: Expr = convert(e.arguments(2))

    lazy val convertedChildren: List[Expr] = e.arguments.toList.map(convert)

    e match {
      case ast.Variable(name) => Var(name)(context.knownTypes.getOrElse(e, throw UnTypedExpr(e)))
      case p@ast.Parameter(name, _) => Param(name)(parameterType(p))

      // Literals
      case astExpr: ast.IntegerLiteral => IntegerLit(astExpr.value)
      case astExpr: ast.DecimalDoubleLiteral => FloatLit(astExpr.value)
      case ast.StringLiteral(value) => StringLit(value)
      case _: ast.True => TrueLit
      case _: ast.False => FalseLit
      case _: ast.ListLiteral => ListLit(convertedChildren)

      case ast.Property(_, ast.PropertyKeyName(name)) =>
        val owner = child0
        val key = PropertyKey(name)
        owner.cypherType.material match {
          case CTVoid => NullLit
          // This means that the node can have any possible label combination, as the user did not specify any constraints
          case CTNode =>
            val propertyType = schema.allCombinations
              .map(l => schema.nodePropertyKeyType(l, name).getOrElse(CTNull))
              .foldLeft(CTVoid: CypherType)(_ join _)
            // User specified label constraints - we can use those for type inference
            EntityProperty(owner, key)(propertyType)
          case CTNode(labels, None) =>
            val propertyType = schema.nodePropertyKeyType(labels, name).getOrElse(CTNull)
            EntityProperty(owner, key)(propertyType)
          case CTNode(labels, Some(qgn)) =>
            val propertyType = context.queryLocalCatalog.schema(qgn).nodePropertyKeyType(labels, name).getOrElse(CTNull)
            EntityProperty(owner, key)(propertyType)
          case CTRelationship(types, None) =>
            val propertyType = schema.relationshipPropertyKeyType(types, name).getOrElse(CTNull)
            EntityProperty(owner, key)(propertyType)
          case CTRelationship(types, Some(qgn)) =>
            val propertyType = context.queryLocalCatalog.schema(qgn).relationshipPropertyKeyType(types, name).getOrElse(CTNull)
            EntityProperty(owner, key)(propertyType)
          case _: CTMap =>
            MapProperty(owner, key)
          case CTDate =>
            DateProperty(owner, key)
          case CTLocalDateTime =>
            LocalDateTimeProperty(owner, key)
          case CTDuration =>
            DurationProperty(owner, key)
          case _ => throw InvalidContainerAccess(e)
        }

      // Predicates
      case _: ast.Ands => Ands(convertedChildren)
      case _: ast.Ors => Ors(convertedChildren)
      case ast.HasLabels(_, labels) => Ands(labels.map(l => HasLabel(child0, Label(l.name))).toSet)
      case _: ast.Not => Not(child0)
      case ast.Equals(f: ast.FunctionInvocation, s: ast.StringLiteral) if f.function == functions.Type =>
        HasType(convert(f.args.head), RelType(s.value))
      case _: ast.Equals => Equals(child0, child1)
      case _: ast.LessThan => LessThan(child0, child1)
      case _: ast.LessThanOrEqual => LessThanOrEqual(child0, child1)
      case _: ast.GreaterThan => GreaterThan(child0, child1)
      case _: ast.GreaterThanOrEqual => GreaterThanOrEqual(child0, child1)
      case _: ast.In => In(child0, child1)
      case _: ast.IsNull => IsNull(child0)
      case _: ast.IsNotNull => IsNotNull(child0)
      case _: ast.StartsWith => StartsWith(child0, child1)
      case _: ast.EndsWith => EndsWith(child0, child1)
      case _: ast.Contains => Contains(child0, child1)

      // Arithmetics
      case ast.Add(lhs, rhs) =>
        val convertedLhs = convert(lhs)
        val convertedRhs = convert(rhs)
        val addType = AddType(convertedLhs.cypherType, convertedRhs.cypherType).getOrElse(
          throw NoSuitableSignatureForExpr(e, Seq(convertedLhs.cypherType, convertedRhs.cypherType))
        )

        Add(convertedLhs, convertedRhs)(addType)

      case s: ast.Subtract =>
        val exprType = s.returnTypeFor(
          BigDecimalSignatures.arithmeticSignature(Addition),
          child0.cypherType, child1.cypherType
        )

        Subtract(child0, child1)(exprType)

      case m@ast.Multiply(lhs, rhs) =>
        val exprType = m.returnTypeFor(
          BigDecimalSignatures.arithmeticSignature(Multiplication),
          child0.cypherType, child1.cypherType
        )

        Multiply(child0, child1)(exprType)

      case d: ast.Divide =>
        val exprType = d.returnTypeFor(
          BigDecimalSignatures.arithmeticSignature(Division),
          child0.cypherType, child1.cypherType
        )

        Divide(child0, child1)(exprType)

      case funcInv: ast.FunctionInvocation =>
        val convertedArgs = funcInv.args.map(convert).toList

        def returnType: CypherType = funcInv.returnTypeFor(convertedArgs.map(_.cypherType): _*)

        val distinct = funcInv.distinct

        def arg0 = convertedArgs(0)

        def arg1 = convertedArgs(1)

        def arg2 = convertedArgs(2)

        funcInv.function match {
          case functions.Id => Id(arg0)
          case functions.Labels => Labels(arg0)
          case functions.Type => Type(arg0)
          case functions.Avg => Avg(arg0)
          case functions.Max => Max(arg0)
          case functions.Min => Min(arg0)
          case functions.Sum => Sum(arg0)
          case functions.Count => Count(arg0, distinct)
          case functions.Collect => Collect(arg0, distinct)
          case functions.Exists => Exists(arg0)
          case functions.Size => Size(arg0)
          case functions.Keys => Keys(arg0)
          case functions.StartNode => StartNodeFunction(arg0)(returnType)
          case functions.EndNode => EndNodeFunction(arg0)(returnType)
          case functions.ToFloat => ToFloat(arg0)
          case functions.ToInteger => ToInteger(arg0)
          case functions.ToString => ToString(arg0)
          case functions.ToBoolean => ToBoolean(arg0)
          case functions.Coalesce =>
            // Special optimisation for coalesce using short-circuit logic
            convertedArgs.map(_.cypherType).indexWhere(!_.isNullable) match {
              case 0 =>
                // first argument is non-nullable; just use it directly without coalesce
                convertedArgs.head
              case -1 =>
                // nothing was non-nullable; keep all args
                Coalesce(convertedArgs)
              case other =>
                // keep only the args up until the first non-nullable (inclusive)
                val relevantArgs = convertedArgs.slice(0, other + 1)
                Coalesce(relevantArgs)
            }
          case functions.Range => Range(arg0, arg1, convertedArgs.lift(2))
          case functions.Substring => Substring(arg0, arg1, convertedArgs.lift(2))
          case functions.Left => Substring(arg0, IntegerLit(0), convertedArgs.lift(1))
          case functions.Right => Substring(arg0, Subtract(Multiply(IntegerLit(-1), arg1)(CTInteger), IntegerLit(1))(CTInteger), None)
          case functions.Replace => Replace(arg0, arg1, arg2)
          case functions.Trim => Trim(arg0)
          case functions.LTrim => LTrim(arg0)
          case functions.RTrim => RTrim(arg0)
          case functions.ToUpper => ToUpper(arg0)
          case functions.ToLower => ToLower(arg0)
          case functions.Properties =>
            val outType = arg0.cypherType.material match {
              case CTVoid => CTNull
              case CTNode(labels, _) =>
                CTMap(schema.nodePropertyKeysForCombinations(schema.combinationsFor(labels)))
              case CTRelationship(types, _) =>
                CTMap(schema.relationshipPropertyKeysForTypes(types))
              case m: CTMap => m
              case _ => throw InvalidArgument(funcInv, funcInv.args(0))
            }
            Properties(arg0)(outType)

          // Logarithmic functions
          case functions.Sqrt => Sqrt(arg0)
          case functions.Log => Log(arg0)
          case functions.Log10 => Log10(arg0)
          case functions.Exp => Exp(arg0)
          case functions.E => E
          case functions.Pi => Pi

          // Numeric functions
          case functions.Abs => Abs(arg0)
          case functions.Ceil => Ceil(arg0)
          case functions.Floor => Floor(arg0)
          case functions.Rand => Rand
          case functions.Round => Round(arg0)
          case functions.Sign => Sign(arg0)

          // Trigonometric functions
          case functions.Acos => Acos(arg0)
          case functions.Asin => Asin(arg0)
          case functions.Atan => Atan(arg0)
          case functions.Atan2 => Atan2(arg0, arg1)
          case functions.Cos => Cos(arg0)
          case functions.Cot => Cot(arg0)
          case functions.Degrees => Degrees(arg0)
          case functions.Haversin => Haversin(arg0)
          case functions.Radians => Radians(arg0)
          case functions.Sin => Sin(arg0)
          case functions.Tan => Tan(arg0)

          // Match by name
          case functions.UnresolvedFunction => funcInv.name match {
            // Time functions
            case f.Timestamp.name => Timestamp
            case f.LocalDateTime.name => LocalDateTime(convertedArgs.headOption)
            case f.Date.name => Date(convertedArgs.headOption)
            case f.Duration.name => Duration(arg0)
            case BigDecimal.name =>
              e.checkNbrArgs(3, convertedArgs.length)
              BigDecimal(arg0, extractLong(arg1), extractLong(arg2))
            case name => throw NotImplementedException(s"Support for converting function '$name' is not yet implemented")
          }

          case a: functions.Function =>
            throw NotImplementedException(s"Support for converting function '${a.name}' is not yet implemented")
        }

      case _: ast.CountStar => CountStar

      // Exists (rewritten Pattern Expressions)
      case org.opencypher.okapi.ir.impl.parse.rewriter.ExistsPattern(subquery, trueVar) =>
        val innerModel = IRBuilder(subquery)(context) match {
          case sq: SingleQuery => sq
          case _ => throw IllegalArgumentException("ExistsPattern only accepts SingleQuery")
        }
        ExistsPatternExpr(
          Var(trueVar.name)(CTBoolean),
          innerModel
        )

      // Case When .. Then .. [Else ..] End
      case ast.CaseExpression(None, alternatives, default) =>
        val convertedAlternatives = alternatives.toList.map { case (left, right) => convert(left) -> convert(right) }
        val maybeConvertedDefault: Option[Expr] = default.map(expr => convert(expr))
        val possibleTypes = convertedAlternatives.map { case (_, thenExpr) => thenExpr.cypherType }
        val defaultCaseType = maybeConvertedDefault.map(_.cypherType).getOrElse(CTNull)
        val returnType = possibleTypes.foldLeft(defaultCaseType)(_ join _)
        CaseExpr(convertedAlternatives, maybeConvertedDefault)(returnType)

      case ast.MapExpression(items) =>
        val convertedMap = items.map { case (key, value) => key.name -> convert(value) }.toMap
        MapExpression(convertedMap)

      // Expression
      case ast.ListSlice(list, Some(from), Some(to)) => ListSliceFromTo(convert(list), convert(from), convert(to))
      case ast.ListSlice(list, None, Some(to)) => ListSliceTo(convert(list), convert(to))
      case ast.ListSlice(list, Some(from), None) => ListSliceFrom(convert(list), convert(from))

      case ast.ContainerIndex(container, index) =>
        val convertedContainer = convert(container)
        val elementType = convertedContainer.cypherType.material match {
          case CTList(eltTyp) => eltTyp
          case CTMap(innerTypes) =>
            index match {
              case ast.Parameter(name, _) =>
                val key = context.parameters(name).cast[String]
                innerTypes.getOrElse(key, CTVoid)
              case ast.StringLiteral(key) => innerTypes.getOrElse(key, CTVoid)
              case _ => innerTypes.values.foldLeft(CTVoid: CypherType)(_ join _).nullable
            }
          case _ => throw InvalidContainerAccess(e)
        }
        ContainerIndex(convertedContainer, convert(index))(elementType)

      case ast.Null() => NullLit

      case RegexMatch(lhs, rhs) => expr.RegexMatch(convert(lhs), convert(rhs))

      case _ =>
        throw NotImplementedException(s"Not yet able to convert expression: $e")
    }
  }

}

object BigDecimalSignatures {

  /**
    * Signature for BigDecimal arithmetics on the type level. The semantics are based on Spark SQL, which in turn
    * is based on Hive and SQL Server. See DecimalPrecision in Apache Spark
    *
    * @param precisionScaleOp
    * @return
    */
  def arithmeticSignature(precisionScaleOp: PrecisionScaleOp): Signature = {
    case Seq(CTBigDecimal(p1, s1), CTBigDecimal(p2, s2)) => Some(CTBigDecimal(precisionScaleOp(p1, s1, p2, s2)))
    case Seq(CTBigDecimal(p, s), CTInteger) => Some(CTBigDecimal(precisionScaleOp(p, s, 20, 0)))
    case Seq(CTInteger, CTBigDecimal(p, s)) => Some(CTBigDecimal(precisionScaleOp(20, 0, p, s)))
    case Seq(_: CTBigDecimal, CTFloat) => Some(CTFloat)
    case Seq(CTFloat, _: CTBigDecimal) => Some(CTFloat)
    case _ => None
  }

  trait PrecisionScaleOp {
    def apply(p1: Int, s1: Int, p2: Int, s2: Int): (Int, Int)
  }

  import Math.max

  case object Addition extends PrecisionScaleOp {
    override def apply(p1: Int, s1: Int, p2: Int, s2: Int): (Int, Int) = {
      max(s1, s2) + max(p1 - s1, p2 - s2) + 1 -> max(s1, s2)
    }
  }

  case object Multiplication extends PrecisionScaleOp {
    override def apply(p1: Int, s1: Int, p2: Int, s2: Int): (Int, Int) = {
      p1 + p2 + 1 -> (s1 + s2)
    }
  }

  case object Division extends PrecisionScaleOp {
    override def apply(p1: Int, s1: Int, p2: Int, s2: Int): (Int, Int) = {
      p1 - s1 + s2 + max(6, s1 + p2 + 1) -> max(6, s1 + p2 + 1)
    }
  }
}

object SignatureTyping {

  /**
    * Determines the output type given a set of signatures and a sequence of argument types.
    * This uses the frontend's signatures, which we enrich with nulls and allow coercions for.
    * Signatures from the frontend may be extended with additional signatures.
    */
  def returnTypeFor(
    signatures: Seq[ast.TypeSignature],
    args: Seq[CypherType],
    extensions: Set[Signature] = Set.empty
  ): Option[CypherType] = {
    // TODO: shrink signature of this call to just take in one collection of Signature
    val expandedSignatures = SignatureConverter.from(signatures)
      .expandWithNulls
      .expandWithSubstitutions(CTFloat, CTInteger)
      .signatures

    val extendedSignatures = expandedSignatures ++ extensions

    val possibleReturnTypes = extendedSignatures.flatMap(sig => sig(args))

    possibleReturnTypes.reduceLeftOption(_ join _)
  }

  implicit class RichOperatorExpression(val o: ast.Expression with OperatorExpression) {
    def returnTypeFor(args: CypherType*): CypherType = {
      SignatureTyping.returnTypeFor(o.signatures, args).getOrElse(throw NoSuitableSignatureForExpr(o, args))
    }

    def returnTypeFor(signatures: Set[Signature], args: CypherType*): CypherType = {
      SignatureTyping.returnTypeFor(o.signatures, args, signatures).getOrElse(throw NoSuitableSignatureForExpr(o, args))
    }

    def returnTypeFor(signature: Signature, args: CypherType*): CypherType = {
      returnTypeFor(Set(signature), args: _*)
    }
  }

  implicit class RichTypeSignatures(val f: ast.FunctionInvocation) {
    def returnTypeFor(args: CypherType*): CypherType = {

      val signatures = FunctionExtensions.getOrElse(f.function.name, f.function) match {
        case t: TypeSignatures => t.signatures
        case _ => throw NoSuitableSignatureForExpr(f, args)
      }

      SignatureTyping.returnTypeFor(signatures, args).getOrElse(throw NoSuitableSignatureForExpr(f, args))
    }
  }

  implicit class ArgumentChecker(val e: ast.Expression) {
    def checkNbrArgs(expected: Int, actual: Int): Unit = {
      if (expected != actual) {
        throw WrongNumberOfArguments(e, expected, actual)
      }
    }
  }
}
