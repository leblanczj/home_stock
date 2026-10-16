package com.homestock.domain

import java.math.BigInteger
import java.time.LocalDate

/** At most three decimal places. Anything else stays an exact reduced fraction. */
fun Rational.formatQuantity(): String {
    if (den == BigInteger.ONE) return num.toString()
    val places = terminatingDecimalPlaces()
    if (places != null && places <= 3) {
        val negative = num.signum() < 0
        val scale = BigInteger.TEN.pow(places)
        val scaled = num.abs() * scale / den
        val digits = scaled.toString().padStart(places + 1, '0')
        val whole = digits.dropLast(places).ifEmpty { "0" }
        val fraction = digits.takeLast(places).trimEnd('0')
        val body = if (fraction.isEmpty()) whole else "$whole.$fraction"
        return if (negative) "-$body" else body
    }
    return "$num/$den"
}

fun formatYuanFromFen(fen: Rational): String {
    val rounded = fen.roundHalfUp()
    val negative = rounded.signum() < 0
    val abs = rounded.abs()
    val yuan = abs.divide(BigInteger.valueOf(100))
    val cents = abs.remainder(BigInteger.valueOf(100)).toInt()
    val text = "$yuan.${cents.toString().padStart(2, '0')}"
    return if (negative) "-$text" else text
}

fun formatYuanFromFen(fen: Long): String = formatYuanFromFen(Rational.of(fen))

fun formatSpeed(speed: Rational?, unit: String): String =
    if (speed == null) "—" else "${speed.formatQuantity()} $unit/天"

fun formatDepletion(depletion: Depletion, today: LocalDate): String = when (depletion) {
    Depletion.AlreadyEmpty -> "已经用完"
    Depletion.Unknown -> "无法估算"
    Depletion.BeyondYear -> "一年内不会用完"
    is Depletion.At -> {
        val time = depletionDateTime(today, depletion.days)
        "%04d-%02d-%02d %02d:%02d".format(
            time.year,
            time.monthValue,
            time.dayOfMonth,
            time.hour,
            time.minute,
        )
    }
}

fun formatHomeLine(row: HomeRow, today: LocalDate): String =
    "${row.brand} ${row.spec} 在手 ${row.onHand.formatQuantity()} ${row.unit} " +
        "速度 ${formatSpeed(row.speed, row.unit)} " +
        "用完 ${formatDepletion(row.depletion, today)} " +
        "${row.lowStockDays} 天内提醒"

fun formatMissingUsage(names: List<String>): String =
    if (names.isEmpty()) {
        "无法估算。接下来的居住组合还没有用量记录。"
    } else {
        "无法估算。${names.joinToString("、")} 还没有用量记录。"
    }

fun residentNames(set: ResidentSet, residents: List<Resident>): List<String> =
    set.ids.map { id -> residents.find { it.id == id }?.displayName() ?: "已删除" }

fun formatExpiryDistance(daysFromToday: Int): String = when {
    daysFromToday < 0 -> "已过期 ${-daysFromToday} 天"
    daysFromToday == 0 -> "今天到期"
    else -> "还有 $daysFromToday 天"
}
