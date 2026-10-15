package com.homestock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormatAndParseTest {
    @Test
    fun quantitiesStayExactAndWithinThreeDecimals() {
        assertEquals("1", Rational.of(1).formatQuantity())
        assertEquals("0.1", Rational.of(1, 10).formatQuantity())
        assertEquals("0.3", Rational.of(3, 10).formatQuantity())
        assertEquals("0.25", Rational.of(1, 4).formatQuantity())
        assertEquals("0.125", Rational.of(1, 8).formatQuantity())
        assertEquals("1/7", Rational.of(1, 7).formatQuantity())
        assertEquals("5/29", Rational.of(5, 29).formatQuantity())
        assertEquals("1/16", Rational.of(1, 16).formatQuantity())
        assertEquals("10.00", formatYuanFromFen(1000))
        assertEquals("20.00", formatYuanFromFen(2000))
        assertEquals("6.67", formatYuanFromFen(Rational.of(2000, 3)))
    }

    @Test
    fun parsersRejectExtraPrecision() {
        assertEquals(Rational.of(3, 10), parseQuantity("0.3"))
        assertEquals(Rational.of(3, 10), parseQuantity("0.300"))
        assertEquals(Rational.of(2), parseQuantity("2"))
        assertNull(parseQuantity("1.2345"))
        assertNull(parseQuantity("-1"))
        assertEquals(2000L, parseYuanToFen("20"))
        assertEquals(2050L, parseYuanToFen("20.5"))
        assertEquals(2050L, parseYuanToFen("20.50"))
        assertNull(parseYuanToFen("20.555"))
    }
}
