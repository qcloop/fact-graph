package gov.irs.factgraph.compnodes

import gov.irs.factgraph.{ Expression, FactDictionary, Factual }
import gov.irs.factgraph.definitions.fact.CompNodeConfigTrait
import gov.irs.factgraph.operators.BinaryOperator
import gov.irs.factgraph.types.*

object StepwiseMultiply extends CompNodeFactory:
  override val Key: String = "StepwiseMultiply"

  private val operator = StepwiseMultiplyOperator()

  def apply(multiplicand: DollarNode, rate: RationalNode): DollarNode =
    DollarNode(
      Expression.Binary(
        multiplicand.expr,
        rate.expr,
        operator,
      ),
    )

  override def fromDerivedConfig(e: CompNodeConfigTrait)(using
      Factual,
  )(using
      FactDictionary,
  ): CompNode =
    val multiplicand = CompNode.getConfigChildNode(e, "Multiplicand")
    val rate = CompNode.getConfigChildNode(e, "Rate")

    (multiplicand, rate) match
      case (m: DollarNode, r: RationalNode) => this(m, r)
      case _                                =>
        throw new UnsupportedOperationException(
          s"invalid child types: ${e.typeName}",
        )

final private class StepwiseMultiplyOperator extends BinaryOperator[Dollar, Dollar, Rational]:
  override protected def operation(x: Dollar, y: Rational): Dollar =
    // Preserve raw denominator-sized steps, including the intentional discard of fractional dollars.
    val whole = BigInt(x.asInstanceOf[BigDecimal].bigDecimal.toBigInteger)
    val result = whole / y.denominatorExact * y.numeratorExact
    Dollar(BigDecimal(new java.math.BigDecimal(result.bigInteger)))
