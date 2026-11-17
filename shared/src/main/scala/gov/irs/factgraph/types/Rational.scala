package gov.irs.factgraph.types

import scala.scalajs.js.annotation.JSExportTopLevel
import scala.util.matching.Regex
import upickle.default.{ readwriter, ReadWriter }

/** Exact fractions. Keep supplied pairs unreduced: StepwiseMultiply depends on the raw rate denominator. */
@JSExportTopLevel("Rational")
final case class Rational protected (private val n: BigInt, private val d: BigInt):
  import Rational.RationalIsFractional._

  require(d != 0, "denominator cannot be zero")

  // Preserve the existing JVM/JavaScript Int constructor and accessors. Engine arithmetic uses the
  // exact components below; a caller requesting an Int must never receive a wrapped value.
  def this(n: Int, d: Int) = this(BigInt(n), BigInt(d))
  def numerator: Int = Rational.checkedInt(n)
  def denominator: Int = Rational.checkedInt(d)
  private[factgraph] def numeratorExact: BigInt = n
  private[factgraph] def denominatorExact: BigInt = d

  private[factgraph] def decimal(scale: Int, mode: java.math.RoundingMode): BigDecimal =
    BigDecimal(new java.math.BigDecimal(n.bigInteger).divide(new java.math.BigDecimal(d.bigInteger), scale, mode))

  def reciprocal: Rational = n match
    case x if x == 0 => throw new ArithmeticException("reciprocal of zero")
    case _           => Rational.exact(d, n)

  def simplify: Rational =
    val divisor = n.gcd(d) * (if (d < 0) -1 else 1)
    Rational.exact(n / divisor, d / divisor)

  def +(that: Rational): Rational = plus(this, that)
  def -(that: Rational): Rational = minus(this, that)
  def *(that: Rational): Rational = times(this, that)
  def /(that: Rational): Rational = div(this, that)
  def <(that: Rational): Boolean = lt(this, that)
  def >(that: Rational): Boolean = gt(this, that)
  def <=(that: Rational): Boolean = lteq(this, that)
  def >=(that: Rational): Boolean = gteq(this, that)

  override def toString: String = s"$n/$d"

given Conversion[Int, Rational] = Rational(_)

object Rational:
  private val Pattern: Regex = """^(-?\d+)/(-?\d+)$""".r

  private[factgraph] def exact(n: BigInt, d: BigInt): Rational = new Rational(n, d)

  private[factgraph] def checkedInt(value: BigInt): Int =
    if (!value.isValidInt) throw new ArithmeticException("Rational component or result exceeds the Int range")
    value.intValue

  // Legacy n/d JSON numbers remain numbers. Wide components use decimal strings so JavaScript's
  // JSON number representation cannot silently round an exact integer beyond its safe range.
  implicit val rationalReadWriter: ReadWriter[Rational] = readwriter[ujson.Value].bimap[Rational](
    x => ujson.Obj("n" -> componentJson(x.n), "d" -> componentJson(x.d)),
    json => exact(readComponent(json("n")), readComponent(json("d"))),
  )

  private def componentJson(value: BigInt): ujson.Value =
    if (value.isValidInt) ujson.Num(value.intValue) else ujson.Str(value.toString)

  private def readComponent(value: ujson.Value): BigInt = value match
    case ujson.Str(s)                                            => BigInt(s)
    case ujson.Num(n) if n.isWhole && n.abs <= 9007199254740991d => BigDecimal(n).toBigInt
    case _                                                       =>
      throw new IllegalArgumentException("Rational components must be exact integers; use strings for wide values")

  @JSExportTopLevel("RationalFactory")
  def apply(n: Int, d: Int): Rational = exact(BigInt(n), BigInt(d))

  @JSExportTopLevel("RationalFactory")
  def apply(i: Int): Rational = exact(BigInt(i), BigInt(1))

  @JSExportTopLevel("RationalFactory")
  def apply(str: String): Rational =
    Numeric[Rational].parseString(str) match
      case Some(x) => x
      case None    => throw new NumberFormatException(s"""For input string: "$str"""")

  implicit object RationalIsFractional extends Fractional[Rational]:
    def compare(x: Rational, y: Rational): Int =
      val cross = (x.n * y.d).compare(y.n * x.d)
      if (x.d.signum == y.d.signum) cross else -cross

    def fromInt(x: Int): Rational = Rational(x)

    def minus(x: Rational, y: Rational): Rational =
      if (x.d == y.d) exact(x.n - y.n, x.d)
      else exact(x.n * y.d - y.n * x.d, x.d * y.d).simplify

    def negate(x: Rational): Rational = exact(-x.n, x.d)

    def plus(x: Rational, y: Rational): Rational =
      if (x.d == y.d) exact(x.n + y.n, x.d)
      else exact(x.n * y.d + y.n * x.d, x.d * y.d).simplify

    def times(x: Rational, y: Rational): Rational = exact(x.n * y.n, x.d * y.d).simplify

    def div(x: Rational, y: Rational): Rational =
      if (y.n == 0) throw new ArithmeticException("divide by zero")
      exact(x.n * y.d, x.d * y.n).simplify

    def parseString(str: String): Option[Rational] = Pattern.findFirstMatchIn(str).filter(_.end == str.length) match
      case Some(m) => Some(exact(BigInt(m.group(1)), BigInt(m.group(2))))
      case None    => None

    override def toDouble(x: Rational): Double =
      throw new UnsupportedOperationException("toDouble might result in a loss of precision")
    override def toFloat(x: Rational): Float =
      throw new UnsupportedOperationException("toFloat might result in a loss of precision")
    override def toInt(x: Rational): Int =
      throw new UnsupportedOperationException("toInt might result in a loss of precision")
    override def toLong(x: Rational): Long =
      throw new UnsupportedOperationException("toLong might result in a loss of precision")
