package gov.irs.factgraph.types

import dev.hegel.{ Generators, Hegel, Settings, TestCase }
import gov.irs.factgraph.compnodes.*
import gov.irs.factgraph.compnodes.given
import gov.irs.factgraph.persisters.{ RationalWrapper, TypeContainer }
import org.scalatest.funspec.AnyFunSpec
import scala.jdk.CollectionConverters.*
import upickle.default.{ read, write }

/** JVM Hegel properties; the fixed overflow regressions also run in the shared JavaScript suite. */
class RationalPropertiesSpec extends AnyFunSpec:
  private def property(name: String)(check: TestCase => Unit): Unit =
    it(name) {
      Hegel.test(tc => check(tc), new Settings().testCases(3000).name(s"Rational:$name"))
    }

  private def wide(tc: TestCase, label: String): BigInt =
    // Bound materialized operands to 256 bits; keep the drawn chunk-list domain unrestricted.
    tc.draw(Generators.lists(Generators.integers()), label)
      .asScala
      .take(8)
      .foldLeft(BigInt(0))((n, chunk) => (n << 32) + BigInt(chunk.intValue))

  private def pair(tc: TestCase, label: String): (BigInt, BigInt) =
    val n = wide(tc, s"${label}Numerator")
    val drawn = wide(tc, s"${label}Denominator")
    (n, if (drawn == 0) BigInt(1) else drawn)

  private def fraction(n: BigInt, d: BigInt): Rational = Rational(s"$n/$d")

  private def components(value: Rational): (BigInt, BigInt) =
    val parts = value.toString.split("/")
    (BigInt(parts(0)), BigInt(parts(1)))

  private def equivalent(actual: Rational, n: BigInt, d: BigInt): Unit =
    val (an, ad) = components(actual)
    assert(ad != 0)
    assert(an * d == n * ad)

  property("Int construction and parsing preserve supplied pairs") { tc =>
    val n = tc.draw(Generators.integers(), "numerator").intValue
    val raw = tc.draw(Generators.integers(), "denominator").intValue
    if (raw == 0) {
      intercept[IllegalArgumentException](Rational(n, raw))
      intercept[IllegalArgumentException](new Rational(n, raw))
    } else {
      val value = Rational(n, raw)
      assert(value.numerator == n)
      assert(value.denominator == raw)
      assert(value.toString == s"$n/$raw")
      assert(Rational(value.toString) == value)
      if (n != 0) assert(value.reciprocal.reciprocal == value)
    }
  }

  property("simplification is exact coprime positive-denominator and idempotent") { tc =>
    val (n, d) = pair(tc, "value")
    val simple = fraction(n, d).simplify
    val (sn, sd) = components(simple)
    equivalent(simple, n, d)
    assert(sd > 0)
    assert(sn.gcd(sd) == 1)
    assert(simple.simplify == simple)
  }

  property("addition preserves exact value and shared denominators") { tc =>
    val (n, d) = pair(tc, "left")
    val (m, e) = pair(tc, "right")
    equivalent(fraction(n, d) + fraction(m, e), n * e + m * d, d * e)
    assert((fraction(n, d) + fraction(m, d)).toString == s"${n + m}/$d")
  }

  property("subtraction preserves exact value and shared denominators") { tc =>
    val (n, d) = pair(tc, "left")
    val (m, e) = pair(tc, "right")
    equivalent(fraction(n, d) - fraction(m, e), n * e - m * d, d * e)
    assert((fraction(n, d) - fraction(m, d)).toString == s"${n - m}/$d")
  }

  property("multiplication remains exact through large and canceling products") { tc =>
    val (n, d) = pair(tc, "left")
    val (m, e) = pair(tc, "right")
    equivalent(fraction(n, d) * fraction(m, e), n * m, d * e)
  }

  property("division is exact and rejects a zero divisor") { tc =>
    val (n, d) = pair(tc, "left")
    val (m, e) = pair(tc, "right")
    if (m == 0) intercept[ArithmeticException](fraction(n, d) / fraction(m, e))
    else equivalent(fraction(n, d) / fraction(m, e), n * e, d * m)
  }

  property("negation preserves the denominator without overflow") { tc =>
    val (n, d) = pair(tc, "value")
    assert(Numeric[Rational].negate(fraction(n, d)).toString == s"${-n}/$d")
  }

  property("reciprocal swaps the raw pair and rejects zero") { tc =>
    val (n, d) = pair(tc, "value")
    if (n == 0) intercept[ArithmeticException](fraction(n, d).reciprocal)
    else assert(fraction(n, d).reciprocal.toString == s"$d/$n")
  }

  property("all ordering operators account for denominator signs and wide products") { tc =>
    val (n, d) = pair(tc, "left")
    val (m, e) = pair(tc, "right")
    val a = fraction(n, d)
    val b = fraction(m, e)
    val leftN = if (d < 0) -n else n
    val rightN = if (e < 0) -m else m
    val expected = (leftN * e.abs).compare(rightN * d.abs)
    assert(Numeric[Rational].compare(a, b).sign == expected.sign)
    assert((a < b) == (expected < 0))
    assert((a <= b) == (expected <= 0))
    assert((a > b) == (expected > 0))
    assert((a >= b) == (expected >= 0))
  }

  property("ordering is transitive") { tc =>
    val (n, d) = pair(tc, "first")
    val (m, e) = pair(tc, "second")
    val (p, q) = pair(tc, "third")
    val a = fraction(n, d)
    val b = fraction(m, e)
    val c = fraction(p, q)
    if (a <= b && b <= c) assert(a <= c)
  }

  property("legacy numeric JSON and wrapper tags remain compatible") { tc =>
    val n = tc.draw(Generators.integers(), "numerator").intValue
    val raw = tc.draw(Generators.integers(), "denominator").intValue
    val d = if (raw == 0) 1 else raw
    val value = Rational(n, d)
    val json = ujson.read(write(value))
    assert(json("n").num == n.toDouble)
    assert(json("d").num == d.toDouble)
    assert(read[Rational](s"""{"n":$n,"d":$d}""") == value)
    val wrapped: TypeContainer = RationalWrapper(value)
    assert(read[TypeContainer](write(wrapped)) == wrapped)
  }

  property("wide values roundtrip exactly through JSON and persistence wrappers") { tc =>
    val (n, d) = pair(tc, "value")
    val value = fraction(n, d)
    assert(read[Rational](write(value)) == value)
    val wrapped: TypeContainer = RationalWrapper(value)
    assert(read[TypeContainer](write(wrapped)) == wrapped)
    if (n.isValidInt) assert(value.numerator == n.intValue)
    else intercept[ArithmeticException](value.numerator)
    if (d.isValidInt) assert(value.denominator == d.intValue)
    else intercept[ArithmeticException](value.denominator)
  }

  property("parsing rejects malformed fractions without accepting partial input") { tc =>
    val raw = tc.draw(Generators.text(), "text")
    if (raw.matches("-?[0-9]+/-?[0-9]+")) {
      val parts = raw.split("/").map(BigInt(_))
      if (parts(1) == 0) intercept[IllegalArgumentException](Rational(raw))
      else equivalent(Rational(raw), parts(0), parts(1))
    } else {
      assert(Numeric[Rational].parseString(raw).isEmpty)
      intercept[NumberFormatException](Rational(raw))
    }
  }

  for ((name, mode) <- Seq(("floor", java.math.RoundingMode.FLOOR), ("ceiling", java.math.RoundingMode.CEILING)))
    property(s"$name uses exact division and rejects out-of-Int results") { tc =>
      val (n, d) = pair(tc, "value")
      val normalized = if (d < 0) -n else n
      val quotient = normalized / d.abs
      val remainder = normalized % d.abs
      val expected =
        if (name == "floor" && remainder < 0) quotient - 1
        else if (name == "ceiling" && remainder > 0) quotient + 1
        else quotient
      val node = RationalNode(fraction(n, d))
      if (expected.isValidInt) {
        val actual = if (name == "floor") Floor(node).get(0).get else Ceiling(node).get(0).get
        assert(actual == expected.intValue)
      } else {
        intercept[ArithmeticException] {
          if (name == "floor") Floor(node).get(0) else Ceiling(node).get(0)
        }
      }
    }

  property("cent truncation remains exact beyond Int sized cents") { tc =>
    val (n, d) = pair(tc, "value")
    val actual = TruncateCents(RationalNode(fraction(n, d))).get(0).get
    equivalent(actual, (n * 100) / d, BigInt(100))
  }

  property("decimal formatting follows requested scale and HALF_UP rounding") { tc =>
    val (n, d) = pair(tc, "value")
    val scale = tc.draw(Generators.integers().min(-10).max(10), "scale").intValue
    val expected = new java.math.BigDecimal(n.bigInteger)
      .divide(new java.math.BigDecimal(d.bigInteger), scale, java.math.RoundingMode.HALF_UP)
      .toString
    assert(AsDecimalString(RationalNode(fraction(n, d)), scale).get(0).get == expected)
  }

  property("direct Dollar conversion rounds only the exact ratio to cents") { tc =>
    val (n, d) = pair(tc, "value")
    val expected = new java.math.BigDecimal(n.bigInteger)
      .divide(new java.math.BigDecimal(d.bigInteger), 2, java.math.RoundingMode.HALF_EVEN)
    val actual = summon[Conversion[Rational, Dollar]].apply(fraction(n, d))
    assert(actual.asInstanceOf[BigDecimal].bigDecimal.compareTo(expected) == 0)
  }

  for (
    operation <- Seq("dollarTimesRational", "rationalTimesDollar", "dollarDividedByRational", "rationalDividedByDollar")
  )
    property(s"$operation preserves wide rational components and cents rounding") { tc =>
      val (n, d) = pair(tc, "rate")
      val cents = tc.draw(Generators.integers(), "cents").intValue
      val amount = java.math.BigDecimal.valueOf(cents.toLong, 2)
      val dollar = Dollar(BigDecimal(amount))
      val rational = fraction(n, d)
      val dn = DollarNode(dollar)
      val rn = RationalNode(rational)
      val numerator =
        if (operation == "rationalDividedByDollar") new java.math.BigDecimal(n.bigInteger)
        else if (operation == "dollarDividedByRational") amount.multiply(new java.math.BigDecimal(d.bigInteger))
        else amount.multiply(new java.math.BigDecimal(n.bigInteger))
      val denominator =
        if (operation == "rationalDividedByDollar") amount.multiply(new java.math.BigDecimal(d.bigInteger))
        else if (operation == "dollarDividedByRational") new java.math.BigDecimal(n.bigInteger)
        else new java.math.BigDecimal(d.bigInteger)
      def evaluate(): Any = operation match
        case "dollarTimesRational"     => Multiply(Seq(dn, rn)).get(0).get
        case "rationalTimesDollar"     => Multiply(Seq(rn, dn)).get(0).get
        case "dollarDividedByRational" => Divide(Seq(dn, rn)).get(0).get
        case _                         => Divide(Seq(rn, dn)).get(0).get
      if (denominator.signum() == 0) intercept[ArithmeticException](evaluate())
      else {
        val expected = numerator.divide(denominator, 2, java.math.RoundingMode.HALF_EVEN)
        assert(evaluate().asInstanceOf[BigDecimal].bigDecimal.compareTo(expected) == 0)
      }
    }

  property("stepwise multiplication preserves raw denominator steps") { tc =>
    val (n, d) = pair(tc, "rate")
    val cents = tc.draw(Generators.integers(), "cents").intValue
    val amount = java.math.BigDecimal.valueOf(cents.toLong, 2)
    val expected = BigInt(amount.toBigInteger) / d * n
    val actual = StepwiseMultiply(DollarNode(Dollar(BigDecimal(amount))), RationalNode(fraction(n, d))).get(0).get
    assert(actual.asInstanceOf[BigDecimal].bigDecimal.compareTo(new java.math.BigDecimal(expected.bigInteger)) == 0)
  }

  it("retains exact large values through arithmetic formatting hashing and JSON") {
    val n = (BigInt(1) << 200000) + 1
    val value = fraction(n, BigInt(3))
    assert(value * Rational(3) == fraction(n, BigInt(1)))
    assert(read[Rational](write(value)) == value)
    assert(value.reciprocal.reciprocal == value)
    assert(value.simplify.hashCode == value.hashCode)
  }
