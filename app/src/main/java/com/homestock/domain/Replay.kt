package com.homestock.domain

import java.time.LocalDate
import kotlin.comparisons.compareBy

data class ProductReplay(
    val onHand: Rational,
    val batches: List<BatchView>,
    val counts: Map<Long, CountMath>,
    val observations: List<DayObservation>,
    val explicit: List<ExplicitUse>,
    val pricePoints: List<PricePoint>,
)

sealed class ReplayOutcome {
    data class Ok(val replay: ProductReplay) : ReplayOutcome()
    data class Failed(val problem: ReplayProblem) : ReplayOutcome()
}

private enum class EventKind(val order: Int) {
    PURCHASE(0),
    DISCARD(1),
    CONSUMPTION(2),
    COUNT(3),
}

private class MutableBatch(
    val key: String,
    val source: BatchSource,
    val sourceId: Long,
    val date: LocalDate,
    val original: Rational,
    var remaining: Rational,
    val priceFen: Long?,
    val discountFen: Long?,
    val channel: String?,
    val expiry: LocalDate?,
    val seq: Long,
)

fun replayProduct(input: LedgerInput, product: Product): ReplayOutcome {
    val purchases = input.purchases.filter { it.productId == product.id && !it.abolished }
    val discards = input.discards.filter { it.productId == product.id && !it.abolished }
    val consumptions = input.consumptions.filter { it.productId == product.id && !it.abolished }
    val counts = input.counts.filter { it.productId == product.id && !it.abolished }
    val explicitDates = consumptions.map { it.date }.toSet()
    val firstPurchase = purchases.minWithOrNull(compareBy<Purchase> { it.date }.thenBy { it.id })

    data class Event(val date: LocalDate, val kind: EventKind, val id: Long, val payload: Any)
    val events = buildList {
        purchases.forEach { add(Event(it.date, EventKind.PURCHASE, it.id, it)) }
        discards.forEach { add(Event(it.date, EventKind.DISCARD, it.id, it)) }
        consumptions.forEach { add(Event(it.date, EventKind.CONSUMPTION, it.id, it)) }
        counts.forEach { add(Event(it.date, EventKind.COUNT, it.id, it)) }
    }.sortedWith(compareBy<Event> { it.date }.thenBy { it.kind.order }.thenBy { it.id })

    val batches = mutableListOf<MutableBatch>()
    var onHand = Rational.ZERO
    val countMath = linkedMapOf<Long, CountMath>()
    val observations = mutableListOf<DayObservation>()
    val explicit = mutableListOf<ExplicitUse>()
    val prices = mutableListOf<PricePoint>()
    var previousCount: LocalDate? = null

    fun deduct(qty: Rational): Boolean {
        if (qty > onHand) return false
        var left = qty
        val ordered = batches.sortedWith(
            compareBy<MutableBatch> { it.expiry == null }
                .thenBy { it.expiry ?: LocalDate.MAX }
                .thenBy { it.date }
                .thenBy { it.seq },
        )
        for (batch in ordered) {
            if (left.isZero()) break
            if (batch.remaining.isZero()) continue
            val take = if (batch.remaining <= left) batch.remaining else left
            batch.remaining -= take
            left -= take
        }
        check(left.isZero())
        onHand -= qty
        return true
    }

    for (event in events) {
        when (event.kind) {
            EventKind.PURCHASE -> {
                val purchase = event.payload as Purchase
                batches += MutableBatch(
                    key = "p-${purchase.id}",
                    source = BatchSource.PURCHASE,
                    sourceId = purchase.id,
                    date = purchase.date,
                    original = purchase.quantity,
                    remaining = purchase.quantity,
                    priceFen = purchase.priceFen,
                    discountFen = purchase.discountFen,
                    channel = purchase.channel,
                    expiry = purchase.expiry,
                    seq = purchase.id,
                )
                onHand += purchase.quantity
                val net = purchase.priceFen - purchase.discountFen
                prices += PricePoint(
                    purchaseId = purchase.id,
                    date = purchase.date,
                    quantity = purchase.quantity,
                    priceFen = purchase.priceFen,
                    discountFen = purchase.discountFen,
                    unitPriceFen = Rational.of(net) / purchase.quantity,
                    channel = purchase.channel,
                )
            }
            EventKind.DISCARD -> {
                val discard = event.payload as Discard
                if (!deduct(discard.quantity)) {
                    return ReplayOutcome.Failed(
                        ReplayProblem(product.id, ProblemKind.DISCARD, discard.id, discard.date),
                    )
                }
            }
            EventKind.CONSUMPTION -> {
                val consumption = event.payload as Consumption
                if (!deduct(consumption.quantity)) {
                    return ReplayOutcome.Failed(
                        ReplayProblem(product.id, ProblemKind.CONSUMPTION, consumption.id, consumption.date),
                    )
                }
                val set = coveringStay(product.residenceId, input, consumption.date)?.residents
                if (set != null && consumption.quantity > Rational.ZERO) {
                    explicit += ExplicitUse(consumption.date, set, consumption.quantity)
                }
            }
            EventKind.COUNT -> {
                val count = event.payload as Count
                val book = onHand
                val inferred = max0(book - count.actual)
                val surplus = max0(count.actual - book)
                if (!inferred.isZero() && !deduct(inferred)) {
                    return ReplayOutcome.Failed(
                        ReplayProblem(product.id, ProblemKind.CONSUMPTION, count.id, count.date),
                    )
                }
                if (!surplus.isZero()) {
                    batches += MutableBatch(
                        key = "s-${count.id}",
                        source = BatchSource.SURPLUS,
                        sourceId = count.id,
                        date = count.date,
                        original = surplus,
                        remaining = surplus,
                        priceFen = null,
                        discountFen = null,
                        channel = null,
                        expiry = null,
                        seq = count.id,
                    )
                    onHand += surplus
                }
                countMath[count.id] = CountMath(count.id, book, count.actual, inferred, surplus)
                val previous = previousCount
                val windowStart = if (previous == null) firstPurchase?.date else previous.plusDays(1)
                windowStart?.takeIf { !it.isAfter(count.date) && inferred > Rational.ZERO }?.let { from ->
                    val days = mutableMapOf<ResidentSet, Int>()
                    var day = from
                    while (!day.isAfter(count.date)) {
                        if (day !in explicitDates) {
                            val set = coveringStay(product.residenceId, input, day)?.residents
                            if (set != null) days[set] = (days[set] ?: 0) + 1
                        }
                        day = day.plusDays(1)
                    }
                    if (days.isNotEmpty()) {
                        observations += DayObservation(count.id, count.date, inferred, days.toMap())
                    }
                }
                previousCount = count.date
            }
        }
    }

    return ReplayOutcome.Ok(
        ProductReplay(
            onHand = onHand,
            batches = batches.map {
                BatchView(
                    key = it.key,
                    source = it.source,
                    sourceId = it.sourceId,
                    date = it.date,
                    original = it.original,
                    remaining = it.remaining,
                    priceFen = it.priceFen,
                    discountFen = it.discountFen,
                    channel = it.channel,
                    expiry = it.expiry,
                )
            },
            counts = countMath,
            observations = observations,
            explicit = explicit,
            pricePoints = prices,
        ),
    )
}

fun abolishedResidenceIds(input: LedgerInput): Set<Long> =
    input.residences.filter { it.abolished }.map { it.id }.toSet()

/** Who is written down for this calendar day. No weekday template. */
fun coveringStay(residenceId: Long, input: LedgerInput, date: LocalDate): Stay? {
    if (residenceId in abolishedResidenceIds(input)) return null
    return input.stays
        .filter { !it.abolished && it.residenceId == residenceId && it.covers(date) }
        .minByOrNull { it.id }
}

/**
 * Forward occupancy. An open "至今" row covers today and every later day.
 * The weekday template is only the previous seven days, and only when no row covers [date].
 */
fun occupancy(residenceId: Long, input: LedgerInput, today: LocalDate, date: LocalDate): Occupancy {
    val stay = coveringStay(residenceId, input, date)
    if (stay != null) return Occupancy(stay.residents, OccupancySource.STAY, stay.id)
    if (residenceId in abolishedResidenceIds(input)) return Occupancy(null, OccupancySource.NONE)
    val templateDate = previousSameWeekday(today, date)
    val template = coveringStay(residenceId, input, templateDate)
    return if (template != null) {
        Occupancy(template.residents, OccupancySource.TEMPLATE, template.id)
    } else {
        Occupancy(null, OccupancySource.NONE)
    }
}

/** The matching weekday inside [today - 7, today - 1]. */
fun previousSameWeekday(today: LocalDate, date: LocalDate): LocalDate {
    val daysBack = ((today.dayOfWeek.value - date.dayOfWeek.value) + 7) % 7
    val back = if (daysBack == 0) 7 else daysBack
    return today.minusDays(back.toLong())
}

fun firstProblem(input: LedgerInput): ReplayProblem? {
    val closedResidences = abolishedResidenceIds(input)
    for (product in input.products) {
        if (product.abolished || product.residenceId in closedResidences) continue
        val outcome = replayProduct(input, product)
        if (outcome is ReplayOutcome.Failed) return outcome.problem
    }
    return null
}
