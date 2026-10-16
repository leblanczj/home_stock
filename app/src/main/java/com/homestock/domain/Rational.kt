package com.homestock.domain

import java.math.BigInteger

/**
 * Exact rational. Money and quantities never use binary floating point.
 * The value is always stored in lowest terms, with a positive denominator.
 */
data class Rational private constructor(
    val num: BigInteger,
    val den: BigInteger,
) : Comparable<Rational> {

    init {
        require(den.signum() > 0) { "denominator must be positive" }
    }

    val signum: Int get() = num.signum()

    fun isZero(): Boolean = num.signum() == 0

    fun abs(): Rational = of(num.abs(), den)

    operator fun unaryMinus(): Rational = of(num.negate(), den)

    operator fun plus(other: Rational): Rational =
        of(num * other.den + other.num * den, den * other.den)

    operator fun minus(other: Rational): Rational =
        of(num * other.den - other.num * den, den * other.den)

    operator fun times(other: Rational): Rational =
        of(num * other.num, den * other.den)

    operator fun div(other: Rational): Rational {
        require(!other.isZero()) { "division by zero" }
        return of(num * other.den, den * other.num)
    }

    override fun compareTo(other: Rational): Int =
        (num * other.den).compareTo(other.num * den)

    /** Floor for a non-negative value. */
    fun floor(): BigInteger = num.divide(den)

    /** Half away from zero, to an integer. */
    fun roundHalfUp(): BigInteger {
        val half = den.divide(BigInteger.TWO)
        return if (num.signum() >= 0) {
            num.add(half).divide(den)
        } else {
            num.subtract(half).divide(den)
        }
    }

    /**
     * Decimal places when the fraction terminates, otherwise null.
     * 1/2 → 1, 1/4 → 2, 1/7 → null.
     */
    fun terminatingDecimalPlaces(): Int? {
        var d = den
        var twos = 0
        var fives = 0
        while (d.mod(BigInteger.TWO) == BigInteger.ZERO) {
            d = d.divide(BigInteger.TWO)
            twos++
        }
        val five = BigInteger.valueOf(5)
        while (d.mod(five) == BigInteger.ZERO) {
            d = d.divide(five)
            fives++
        }
        if (d != BigInteger.ONE) return null
        return maxOf(twos, fives)
    }

    override fun toString(): String = if (den == BigInteger.ONE) num.toString() else "$num/$den"

    companion object {
        val ZERO: Rational = of(BigInteger.ZERO, BigInteger.ONE)
        val ONE: Rational = of(BigInteger.ONE, BigInteger.ONE)

        fun of(n: Long, d: Long = 1L): Rational = of(BigInteger.valueOf(n), BigInteger.valueOf(d))

        fun of(n: BigInteger, d: BigInteger = BigInteger.ONE): Rational {
            require(d.signum() != 0) { "denominator must not be zero" }
            var num = n
            var den = d
            if (den.signum() < 0) {
                num = num.negate()
                den = den.negate()
            }
            val g = num.gcd(den)
            return Rational(num / g, den / g)
        }

        fun max(a: Rational, b: Rational): Rational = if (a >= b) a else b

        fun min(a: Rational, b: Rational): Rational = if (a <= b) a else b
    }
}

fun max0(value: Rational): Rational = if (value.signum < 0) Rational.ZERO else value
