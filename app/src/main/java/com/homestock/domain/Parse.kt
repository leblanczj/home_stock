package com.homestock.domain

import java.math.BigInteger

fun parseQuantity(text: String): Rational? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("-")) return null
    val parts = trimmed.split(".")
    if (parts.size > 2) return null
    if (parts.size == 1) {
        if (parts[0].isEmpty() || parts[0].any { !it.isDigit() }) return null
        return Rational.of(parts[0].toLong())
    }
    val wholeText = parts[0].ifEmpty { "0" }
    val fraction = parts[1]
    if (wholeText.any { !it.isDigit() } || fraction.isEmpty() || fraction.length > 3 || fraction.any { !it.isDigit() }) {
        return null
    }
    val scale = BigInteger.TEN.pow(fraction.length)
    val num = BigInteger(wholeText) * scale + BigInteger(fraction)
    return Rational.of(num, scale)
}

/** Yuan text with at most two decimal places, stored as fen. */
fun parseYuanToFen(text: String): Long? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.startsWith("-")) return null
    val parts = trimmed.split(".")
    if (parts.size > 2) return null
    if (parts[0].isEmpty() || parts[0].any { !it.isDigit() }) return null
    val whole = parts[0].toLong()
    if (parts.size == 1) return whole * 100
    val fraction = parts[1]
    if (fraction.isEmpty() || fraction.length > 2 || fraction.any { !it.isDigit() }) return null
    return whole * 100 + fraction.padEnd(2, '0').toLong()
}
