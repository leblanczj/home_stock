package com.homestock.domain

import java.time.LocalDate
import java.time.LocalDateTime

const val FORECAST_LIMIT_DAYS = 366

fun forecast(
    onHand: Rational,
    today: LocalDate,
    rateOf: (ResidentSet) -> Rational?,
    setOn: (LocalDate) -> ResidentSet?,
): Forecast {
    if (onHand.isZero()) return Forecast(Depletion.AlreadyEmpty, null)
    if (onHand < Rational.ZERO) return Forecast(Depletion.Unknown, null)
    var remaining = onHand
    var elapsed = Rational.ZERO
    for (i in 0 until FORECAST_LIMIT_DAYS) {
        val date = today.plusDays(i.toLong())
        val set = setOn(date)
        if (set == null) {
            elapsed += Rational.ONE
            continue
        }
        val rate = rateOf(set)
        if (rate == null) return Forecast(Depletion.Unknown, null, missing = set)
        if (rate.isZero()) {
            elapsed += Rational.ONE
            continue
        }
        if (remaining > rate) {
            remaining -= rate
            elapsed += Rational.ONE
        } else {
            elapsed += remaining / rate
            return Forecast(Depletion.At(elapsed), onHand / elapsed)
        }
    }
    return Forecast(Depletion.BeyondYear, null)
}

fun lookbackWindow(today: LocalDate, lookback: Lookback): ClosedRange<LocalDate> {
    val days = when (lookback) {
        Lookback.WEEK -> 7L
        Lookback.MONTH -> 30L
        Lookback.YEAR -> 365L
    }
    return today.minusDays(days - 1)..today
}

fun depletionDate(today: LocalDate, days: Rational): LocalDate =
    today.plusDays(days.floor().toLong())

fun depletionDateTime(today: LocalDate, days: Rational): LocalDateTime {
    val whole = days.floor()
    val fraction = days - Rational.of(whole)
    val date = today.plusDays(whole.toLong())
    if (fraction.isZero()) return date.atStartOfDay()
    val seconds = (fraction * Rational.of(86_400)).roundHalfUp()
    return date.atStartOfDay().plusSeconds(seconds.toLong())
}

fun isLowStock(onHand: Rational, depletion: Depletion, today: LocalDate, warnDays: Int): Boolean {
    if (onHand.isZero()) return true
    if (depletion !is Depletion.At) return false
    val date = depletionDate(today, depletion.days)
    return !date.isAfter(today.plusDays(warnDays.toLong()))
}

fun isExpiring(remaining: Rational, expiry: LocalDate?, today: LocalDate, warnDays: Int): Boolean {
    if (remaining <= Rational.ZERO || expiry == null) return false
    return !expiry.isAfter(today.plusDays(warnDays.toLong()))
}
