package com.homestock.domain

import java.time.LocalDate

enum class Lookback {
    WEEK,
    MONTH,
    YEAR,
}

/** Sorted, unique resident ids. Identity of a situation, not a headcount. */
data class ResidentSet private constructor(val ids: List<Long>) : Comparable<ResidentSet> {
    override fun compareTo(other: ResidentSet): Int {
        val n = minOf(ids.size, other.ids.size)
        for (i in 0 until n) {
            val c = ids[i].compareTo(other.ids[i])
            if (c != 0) return c
        }
        return ids.size.compareTo(other.ids.size)
    }

    override fun toString(): String = ids.joinToString(",", "{", "}")

    companion object {
        fun of(ids: Collection<Long>): ResidentSet {
            val sorted = ids.distinct().sorted()
            require(sorted.isNotEmpty()) { "a resident set needs at least one person" }
            return ResidentSet(sorted)
        }

        fun of(vararg ids: Long): ResidentSet = of(ids.toList())
    }
}

data class Resident(
    val id: Long,
    val name: String,
    val deleted: Boolean = false,
    val deletedName: String? = null,
    val abolished: Boolean = false,
) {
    fun displayName(): String = if (deleted) deletedName ?: name else name

    fun markDeleted(): Resident = copy(deleted = true, deletedName = name)
}

data class Residence(
    val id: Long,
    val name: String,
    val abolished: Boolean = false,
)

data class Stay(
    val id: Long,
    val residenceId: Long,
    val start: LocalDate,
    val end: LocalDate?,
    val residents: ResidentSet,
    val abolished: Boolean = false,
) {
    fun covers(date: LocalDate): Boolean {
        if (date < start) return false
        val closed = end
        return closed == null || date <= closed
    }
}

data class Product(
    val id: Long,
    val residenceId: Long,
    val brand: String,
    val spec: String,
    val unit: String,
    val lowStockDays: Int = 5,
    val expiryWarnDays: Int = 3,
    val abolished: Boolean = false,
)

data class Purchase(
    val id: Long,
    val productId: Long,
    val date: LocalDate,
    val quantity: Rational,
    val priceFen: Long,
    val discountFen: Long,
    val channel: String,
    val expiry: LocalDate?,
    val abolished: Boolean = false,
)

data class Consumption(
    val id: Long,
    val productId: Long,
    val date: LocalDate,
    val quantity: Rational,
    val abolished: Boolean = false,
)

data class Discard(
    val id: Long,
    val productId: Long,
    val date: LocalDate,
    val quantity: Rational,
    val reason: String? = null,
    val abolished: Boolean = false,
)

data class Count(
    val id: Long,
    val productId: Long,
    val date: LocalDate,
    val actual: Rational,
    val abolished: Boolean = false,
)

data class LedgerInput(
    val residents: List<Resident> = emptyList(),
    val residences: List<Residence> = emptyList(),
    val stays: List<Stay> = emptyList(),
    val products: List<Product> = emptyList(),
    val purchases: List<Purchase> = emptyList(),
    val consumptions: List<Consumption> = emptyList(),
    val discards: List<Discard> = emptyList(),
    val counts: List<Count> = emptyList(),
    val lookback: Lookback = Lookback.WEEK,
)

enum class BatchSource { PURCHASE, SURPLUS }

data class BatchView(
    val key: String,
    val source: BatchSource,
    val sourceId: Long,
    val date: LocalDate,
    val original: Rational,
    val remaining: Rational,
    val priceFen: Long?,
    val discountFen: Long?,
    val channel: String?,
    val expiry: LocalDate?,
)

data class CountMath(
    val countId: Long,
    val book: Rational,
    val actual: Rational,
    val inferred: Rational,
    val surplus: Rational,
)

data class PricePoint(
    val purchaseId: Long,
    val date: LocalDate,
    val quantity: Rational,
    val priceFen: Long,
    val discountFen: Long,
    val unitPriceFen: Rational,
    val channel: String,
)

enum class ProblemKind { CONSUMPTION, DISCARD }

data class ReplayProblem(
    val productId: Long,
    val kind: ProblemKind,
    val entryId: Long,
    val date: LocalDate,
)

data class DayObservation(
    val countId: Long,
    val date: LocalDate,
    val q: Rational,
    val days: Map<ResidentSet, Int>,
)

data class ExplicitUse(
    val date: LocalDate,
    val set: ResidentSet,
    val quantity: Rational,
)

enum class OccupancySource { STAY, TEMPLATE, NONE }

data class Occupancy(
    val set: ResidentSet?,
    val source: OccupancySource,
    val stayId: Long? = null,
)

sealed interface Depletion {
    data object AlreadyEmpty : Depletion
    data object Unknown : Depletion
    data object BeyondYear : Depletion
    data class At(val days: Rational) : Depletion
}

data class Forecast(
    val depletion: Depletion,
    val speed: Rational?,
    val missing: ResidentSet? = null,
)

data class SchemeWeights(
    val scheme1: Int = 0,
    val scheme2: Int = 0,
    val scheme3: Int = 0,
    val scheme4: Int = 0,
)

data class SetRate(
    val rate: Rational?,
    val weights: SchemeWeights,
    val schemeRates: Map<Int, Rational>,
)

data class Allocation(
    val countId: Long,
    val scheme: Int,
    val shares: Map<ResidentSet, Rational>,
)

data class RateModel(
    val bySet: Map<ResidentSet, SetRate> = emptyMap(),
    val allocations: List<Allocation> = emptyList(),
) {
    fun rate(set: ResidentSet): Rational? = bySet[set]?.rate
}

/** The only figures a home row is allowed to show. */
data class HomeRow(
    val productId: Long,
    val residenceId: Long,
    val brand: String,
    val spec: String,
    val unit: String,
    val onHand: Rational,
    val speed: Rational?,
    val depletion: Depletion,
    val lowStockDays: Int,
)

data class ExpiryRow(
    val productId: Long,
    val residenceId: Long,
    val brand: String,
    val spec: String,
    val unit: String,
    val remaining: Rational,
    val expiry: LocalDate,
    val channel: String?,
    val warnDays: Int,
    val daysFromToday: Int,
)

data class StayConflict(
    val stayA: Long,
    val stayB: Long,
    val message: String,
)
