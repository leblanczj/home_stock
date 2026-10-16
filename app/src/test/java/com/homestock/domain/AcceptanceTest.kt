package com.homestock.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AcceptanceTest {
    private val a = 1L
    private val b = 2L
    private val home = 1L
    private val parents = 2L
    private val product = 1L
    private val setA = ResidentSet.of(a)
    private val setB = ResidentSet.of(b)
    private val setAb = ResidentSet.of(a, b)

    @Test
    fun firstWeekSplitsByDaysAndDoesNotShowTheSplit() {
        val today = date("2026-05-11")
        val input = weekOne()
        val math = project(input, today).math(product)!!
        val allocation = math.rates.allocations.single()
        assertEquals(2, allocation.scheme)
        assertEquals(r(5, 7), allocation.shares.getValue(setA))
        assertEquals(r(2, 7), allocation.shares.getValue(setAb))
        assertEquals(r(1, 7), math.rates.rate(setA))
        assertEquals(r(1, 7), math.rates.rate(setAb))
        assertEquals(0, math.rates.bySet.getValue(setA).weights.scheme3)
        assertEquals(0, math.rates.bySet.getValue(setAb).weights.scheme3)

        val row = math.homeRow()
        assertEquals(r(1), row.onHand)
        assertEquals(r(1, 7), row.speed)
        val at = row.depletion as Depletion.At
        assertEquals(r(7), at.days)
        assertEquals("2026-05-18 00:00", formatDepletion(at, today))
        assertEquals(row.speed, row.onHand / at.days)
        assertFalse(math.lowStock)
        assertTrue(project(input, today).lowStockRows(home).isEmpty())
        assertTrue(project(input, today).expiryRows(home, today).isEmpty())

        val line = formatHomeLine(row, today)
        assertTrue(line.contains("1/7"))
        assertFalse(line.contains("5/7"))
        assertFalse(line.contains("2/7"))
        assertFalse(line.contains("方案"))
        assertFalse(line.contains("权重"))
        assertFalse(line.contains("{"))
        assertEquals(r(10), math.pricePoints.single().unitPriceFen / r(100))
    }

    @Test
    fun repeatingTheSameFivePlusTwoKeepsSchemeThreeAtZero() {
        val direct = computeRates(
            listOf(
                DayObservation(1, date("2026-05-10"), r(1), mapOf(setA to 5, setAb to 2)),
                DayObservation(2, date("2026-05-17"), r(1), mapOf(setA to 5, setAb to 2)),
            ),
            emptyList(),
        )
        assertEquals(r(1, 7), direct.rate(setA))
        assertEquals(r(1, 7), direct.rate(setAb))
        assertEquals(0, direct.bySet.getValue(setA).weights.scheme3)
        assertTrue(direct.allocations.all { it.scheme == 2 })

        val input = weekOne(lookback = Lookback.YEAR).copy(
            purchases = weekOne().purchases + purchase(id = 2, date = "2026-05-11", qty = r(1)),
            counts = weekOne().counts + count(id = 2, date = "2026-05-17", actual = r(1)),
            stays = weekOne().stays + listOf(
                stay(3, "2026-05-11", "2026-05-15", setA),
                stay(4, "2026-05-16", "2026-05-17", setAb),
            ),
        )
        val math = project(input, date("2026-05-18")).math(product)!!
        assertEquals(r(1, 7), math.rates.rate(setA))
        assertEquals(r(1, 7), math.rates.rate(setAb))
        assertEquals(0, math.rates.bySet.getValue(setA).weights.scheme3)
        assertEquals(0, math.rates.bySet.getValue(setAb).weights.scheme3)
        assertTrue(math.rates.allocations.all { it.scheme == 2 })
        assertTrue(math.rates.allocations.all { it.shares[setA] == r(5, 7) && it.shares[setAb] == r(2, 7) })
    }

    @Test
    fun secondWeekUsesOnlyBAndMonthLookbackDoesNotMergePeople() {
        val today = date("2026-05-18")
        val week = project(weekTwo(Lookback.WEEK), today).math(product)!!
        assertEquals(r(1, 10), week.rates.rate(setB))
        assertNull(week.rates.rate(setA))
        assertNull(week.rates.rate(setAb))
        assertNotEqualRates(week.rates.rate(setB), r(1, 7))
        assertNotEqualRates(week.rates.rate(setB), r(1, 14))
        assertEquals(0, week.rates.bySet.getValue(setB).weights.scheme3)
        val count = week.counts.getValue(2)
        assertEquals(r(1), count.book)
        assertEquals(r(7, 10), count.inferred)
        assertEquals(r(3, 10), week.onHand)

        val row = week.homeRow()
        assertEquals(r(1, 10), row.speed)
        val at = row.depletion as Depletion.At
        assertEquals(r(3), at.days)
        assertEquals("2026-05-21 00:00", formatDepletion(at, today))
        assertTrue(week.lowStock)
        assertEquals(listOf(product), project(weekTwo(Lookback.WEEK), today).lowStockRows(home).map { it.productId })

        val shorter = weekTwo(Lookback.WEEK).let { input ->
            input.copy(products = input.products.map { it.copy(lowStockDays = 2) })
        }
        val shortened = project(shorter, today).math(product)!!
        assertFalse(shortened.lowStock)
        assertEquals(week.forecast.depletion, shortened.forecast.depletion)
        assertTrue(project(shorter, today).lowStockRows(home).isEmpty())

        val month = project(weekTwo(Lookback.MONTH), today).math(product)!!
        assertEquals(r(1, 7), month.rates.rate(setA))
        assertEquals(r(1, 7), month.rates.rate(setAb))
        assertEquals(r(1, 10), month.rates.rate(setB))
        assertEquals(0, month.rates.bySet.getValue(setA).weights.scheme3)
        assertEquals(r(1, 10), month.homeRow().speed)
        assertNotEqualRates(month.homeRow().speed, r(33, 280))
        val line = formatHomeLine(month.homeRow(), today)
        assertFalse(line.contains("1/7"))
        assertFalse(line.contains("33/280"))
        assertFalse(line.contains("方案"))
        assertFalse(line.contains("权重"))
        assertTrue(project(weekTwo(Lookback.MONTH), today).expiryRows(home, today).isEmpty())
    }

    @Test
    fun schemeThreeSeparatesTwoSetsAndForecastUsesOnlyTheBlendedSpeed() {
        val direct = computeRates(
            listOf(
                DayObservation(1, date("2026-05-24"), r(1), mapOf(setA to 5, setAb to 2)),
                DayObservation(2, date("2026-05-31"), r(7, 10), mapOf(setA to 7)),
            ),
            emptyList(),
        )
        assertEquals(r(1, 10), direct.rate(setA))
        assertEquals(r(1, 4), direct.rate(setAb))
        assertNull(direct.rate(setB))
        assertEquals(14, direct.bySet.getValue(setA).weights.scheme3)
        assertEquals(14, direct.bySet.getValue(setAb).weights.scheme3)
        assertEquals(0, direct.bySet.getValue(setA).weights.scheme2)
        assertEquals(0, direct.bySet.getValue(setAb).weights.scheme2)
        assertEquals(0, direct.bySet.getValue(setA).weights.scheme1)
        val first = direct.allocations.single { it.countId == 1L }
        assertEquals(3, first.scheme)
        assertEquals(r(1, 2), first.shares.getValue(setA))
        assertEquals(r(1, 2), first.shares.getValue(setAb))

        val today = date("2026-06-01")
        val math = project(schemeThreeLedger(), today).math(product)!!
        assertEquals(r(1, 10), math.rates.rate(setA))
        assertEquals(r(1, 4), math.rates.rate(setAb))
        assertNull(math.rates.rate(setB))
        assertEquals(r(1), math.onHand)
        val at = math.forecast.depletion as Depletion.At
        assertEquals(r(29, 5), at.days)
        assertEquals(r(5, 29), math.forecast.speed)
        assertEquals(math.forecast.speed, math.homeRow().speed)
        val line = formatHomeLine(math.homeRow(), today)
        assertTrue(line.contains("5/29"))
        assertFalse(line.contains("方案"))
        assertFalse(line.contains("1/10"))
        assertFalse(line.contains("1/4"))
    }

    @Test
    fun negativeRateIsClampedAndSharesStillSumToQ() {
        val model = computeRates(
            listOf(
                DayObservation(1, date("2026-05-01"), r(10), mapOf(setA to 1)),
                DayObservation(2, date("2026-05-02"), r(1), mapOf(setA to 1, setB to 1)),
            ),
            emptyList(),
        )
        assertEquals(r(11, 2), model.rate(setA))
        assertEquals(r(0), model.rate(setB))
        val second = model.allocations.single { it.countId == 2L }
        assertEquals(r(1), second.shares.values.fold(Rational.ZERO) { acc, share -> acc + share })
    }

    @Test
    fun expiryListUsesEachProductsOwnDays() {
        val today = date("2026-05-11")
        val input = LedgerInput(
            residences = listOf(Residence(home, "家")),
            products = listOf(
                item(1, expiryWarnDays = 3),
                item(2, brand = "肥皂", expiryWarnDays = 1),
                item(3, brand = "牙膏", expiryWarnDays = 3),
                item(4, brand = "垃圾袋", expiryWarnDays = 3),
            ),
            purchases = listOf(
                purchase(1, productId = 1, date = "2026-05-01", qty = r(1), expiry = "2026-05-13"),
                purchase(2, productId = 2, date = "2026-05-01", qty = r(1), expiry = "2026-05-10"),
                purchase(3, productId = 3, date = "2026-05-01", qty = r(1), expiry = null),
                purchase(4, productId = 4, date = "2026-05-01", qty = r(1), expiry = "2026-05-13"),
            ),
            consumptions = listOf(consumption(1, productId = 4, date = "2026-05-02", qty = r(1))),
        )
        assertEquals(listOf(2L, 1L), project(input, today).expiryRows(home, today).map { it.productId })
        val tightened = input.copy(products = input.products.map { if (it.id == 1L) it.copy(expiryWarnDays = 1) else it })
        assertTrue(project(tightened, today).expiryRows(home, today).none { it.productId == 1L })
        assertEquals(listOf(2L), project(tightened, today).expiryRows(home, today).map { it.productId })
        val expired = project(tightened, today).expiryRows(home, today).single()
        assertTrue(expired.daysFromToday < 0)
        assertEquals("已过期 1 天", formatExpiryDistance(expired.daysFromToday))
    }

    @Test
    fun emptyHandIsLowStockEvenWhenSpeedIsUnknown() {
        val today = date("2026-05-11")
        val input = LedgerInput(
            residences = listOf(Residence(home, "家")),
            products = listOf(item(product)),
            purchases = listOf(purchase(1, date = "2026-05-04", qty = r(1))),
            consumptions = listOf(consumption(1, date = "2026-05-05", qty = r(1))),
        )
        val math = project(input, today).math(product)!!
        assertEquals(r(0), math.onHand)
        assertNull(math.rates.rate(setA))
        assertEquals(Depletion.AlreadyEmpty, math.forecast.depletion)
        assertTrue(math.lowStock)
        assertEquals("已经用完", formatDepletion(math.homeRow().depletion, today))
    }

    @Test
    fun firstExpiryFirstOutAndDiscardDoesNotChangeSpeed() {
        val today = date("2026-05-08")
        fun ledger(discard: Boolean) = LedgerInput(
            lookback = Lookback.WEEK,
            residents = people(),
            residences = listOf(Residence(home, "家")),
            stays = listOf(stay(1, "2026-05-01", "2026-05-08", setA)),
            products = listOf(item(product)),
            purchases = listOf(
                purchase(1, date = "2026-05-01", qty = r(7), expiry = "2026-06-01"),
                purchase(2, date = "2026-05-08", qty = r(1), expiry = "2026-05-20"),
                purchase(3, date = "2026-05-08", qty = r(5), expiry = "2026-05-30"),
                purchase(4, date = "2026-05-08", qty = r(3), expiry = null),
            ),
            counts = listOf(count(1, date = "2026-05-07", actual = r(0))),
            consumptions = if (discard) emptyList() else listOf(consumption(1, date = "2026-05-08", qty = r(2))),
            discards = if (discard) listOf(discard(1, date = "2026-05-08", qty = r(2))) else emptyList(),
        )
        val consumed = project(ledger(discard = false), today).math(product)!!
        val discarded = project(ledger(discard = true), today).math(product)!!
        val baseline = project(ledger(discard = true).copy(discards = emptyList()), today).math(product)!!
        assertEquals(r(0), consumed.batches.remainingOf(2))
        assertEquals(r(4), consumed.batches.remainingOf(3))
        assertEquals(r(3), consumed.batches.remainingOf(4))
        assertEquals(consumed.batches.map { it.remaining }, discarded.batches.map { it.remaining })
        assertEquals(baseline.rates.rate(setA), discarded.rates.rate(setA))
        assertEquals(r(1), discarded.rates.rate(setA))
        assertEquals(r(9, 8), consumed.rates.rate(setA))
    }

    @Test
    fun explicitConsumptionIsNotCountedAgainByTheNextStocktake() {
        val input = LedgerInput(
            residents = people(),
            residences = listOf(Residence(home, "家")),
            stays = listOf(stay(1, "2026-05-01", "2026-05-03", setA)),
            products = listOf(item(product)),
            purchases = listOf(purchase(1, date = "2026-05-01", qty = r(5))),
            consumptions = listOf(consumption(1, date = "2026-05-02", qty = r(2))),
            counts = listOf(count(1, date = "2026-05-03", actual = r(2))),
        )
        val math = project(input, date("2026-05-03")).math(product)!!
        val count = math.counts.getValue(1)
        assertEquals(r(3), count.book)
        assertEquals(r(1), count.inferred)
        assertEquals(r(0), count.surplus)
        assertEquals(r(1), math.rates.allocations.single { it.scheme != 4 }.let { it.shares.values.fold(Rational.ZERO) { a, s -> a + s } })
        assertEquals(1, math.rates.bySet.getValue(setA).weights.scheme4)
    }

    @Test
    fun rejectsOverlapDiscountAndStockThatWouldGoNegative() {
        val base = LedgerInput(
            residents = people(),
            residences = listOf(Residence(home, "家"), Residence(parents, "父母家")),
            products = listOf(item(product)),
            purchases = listOf(purchase(1, date = "2026-05-01", qty = r(1))),
        )
        val tooMuch = base.copy(consumptions = listOf(consumption(1, date = "2026-05-02", qty = r(2))))
        val problem = firstProblem(tooMuch)
        assertEquals(ProblemKind.CONSUMPTION, problem?.kind)
        assertEquals(1L, problem?.entryId)
        assertNull(validatePurchase(r(1), 100, 0, "超市"))
        assertTrue(validatePurchase(r(1), 100, 101, "超市")!!.contains("优惠"))
        assertTrue(validatePurchase(r(0), 100, 0, "超市")!!.contains("数量"))
        assertTrue(validatePurchase(r(1), 100, 0, "  ")!!.contains("渠道"))

        val open = stay(1, "2026-05-04", null, setA)
        val later = stay(2, "2026-05-18", "2026-05-18", setB)
        val overlap = base.copy(stays = listOf(open, later))
        assertTrue(stayConflict(overlap)!!.message.contains("重叠"))
        assertNull(stayConflict(overlap.copy(stays = listOf(open.copy(abolished = true), later))))
        assertNull(stayConflict(overlap.copy(stays = listOf(open.copy(end = date("2026-05-17")), later))))

        val shared = base.copy(
            stays = listOf(
                stay(1, "2026-05-01", "2026-05-10", setA),
                stay(2, "2026-05-05", "2026-05-06", setA, residenceId = parents),
            ),
        )
        assertTrue(stayConflict(shared)!!.message.contains("两个住所"))
        val different = shared.copy(
            stays = listOf(
                stay(1, "2026-05-01", "2026-05-10", setA),
                stay(2, "2026-05-05", "2026-05-06", setB, residenceId = parents),
            ),
        )
        assertNull(stayConflict(different))

        val plan = planResidenceDeletion(base.copy(stays = listOf(open)), home, photoCount = 2)!!
        assertEquals(1, plan.products)
        assertEquals(1, plan.batches)
        assertEquals(2, plan.photos)
        assertTrue(plan.message().contains("不会转移到其他住所"))
        assertTrue(plan.message().contains("居住者名单会保留"))
        assertEquals(1, base.products.size)
    }

    @Test
    fun deletingAResidentKeepsTheOldSetAndName() {
        val input = weekOne()
        val removed = input.copy(residents = input.residents.map { if (it.id == b) it.markDeleted() else it })
        val stay = removed.stays.first { it.residents == setAb }
        assertEquals(listOf(a, b), stay.residents.ids)
        assertEquals("B", removed.residents.first { it.id == b }.displayName())
        assertFalse(removed.residents.first { it.id == b }.deletedName == null)
        val before = project(input, date("2026-05-11")).math(product)!!
        val after = project(removed, date("2026-05-11")).math(product)!!
        assertEquals(before.rates.rate(setAb), after.rates.rate(setAb))
        val renamed = input.copy(residents = input.residents.map { if (it.id == a) it.copy(name = "甲") else it })
        assertEquals(before.homeRow().speed, project(renamed, date("2026-05-11")).math(product)!!.homeRow().speed)
        assertEquals(setA, renamed.stays.first().residents)
    }

    @Test
    fun editingAbolishingAndDeletingReplaysTheStocktakeGap() {
        val today = date("2026-05-11")
        val original = weekOne()
        assertEquals(r(1), project(original, today).math(product)!!.counts.getValue(1).inferred)

        val edited = original.copy(purchases = original.purchases.map { it.copy(quantity = r(3)) })
        val editedMath = project(edited, today).math(product)!!
        assertEquals(r(3), editedMath.counts.getValue(1).book)
        assertEquals(r(2), editedMath.counts.getValue(1).inferred)
        assertEquals(r(0), editedMath.counts.getValue(1).surplus)
        assertEquals(r(1), editedMath.onHand)
        assertNotEqualRates(editedMath.counts.getValue(1).inferred, r(1))

        val abolished = edited.copy(purchases = edited.purchases.map { it.copy(abolished = true) })
        val abolishedMath = project(abolished, today).math(product)!!
        assertTrue(abolishedMath.pricePoints.isEmpty())
        assertTrue(abolishedMath.batches.none { it.source == BatchSource.PURCHASE })
        assertTrue(project(abolished, today).expiryRows(home, today).isEmpty())
        assertEquals(r(0), abolishedMath.counts.getValue(1).book)
        assertEquals(r(1), abolishedMath.counts.getValue(1).actual)
        assertEquals(r(1), abolishedMath.counts.getValue(1).surplus)
        assertEquals(r(0), abolishedMath.counts.getValue(1).inferred)
        assertEquals(r(1), abolishedMath.onHand)
        assertTrue(abolished.purchases.single().abolished)

        val restored = project(edited, today).math(product)!!
        assertEquals(r(3), restored.counts.getValue(1).book)
        assertEquals(r(2), restored.counts.getValue(1).inferred)
        assertEquals(1, restored.pricePoints.size)

        val withoutCount = edited.copy(counts = emptyList())
        val cleared = project(withoutCount, today).math(product)!!
        assertTrue(cleared.counts.isEmpty())
        assertEquals(r(3), cleared.onHand)
        assertEquals(r(3), cleared.batches.remainingOf(1))
    }

    @Test
    fun openEndedStayCoversTheFutureUntilItIsClosed() {
        val today = date("2026-05-18")
        val input = LedgerInput(
            residents = people(),
            residences = listOf(Residence(home, "家")),
            stays = listOf(stay(1, "2026-05-11", null, setB)),
        )
        val open = occupancy(home, input, today, today)
        assertEquals(OccupancySource.STAY, open.source)
        assertEquals(setB, open.set)
        val closed = input.copy(stays = listOf(stay(1, "2026-05-11", "2026-05-17", setB)))
        val templated = occupancy(home, closed, today, today)
        assertEquals(OccupancySource.TEMPLATE, templated.source)
        assertEquals(setB, templated.set)

        val contrast = LedgerInput(
            lookback = Lookback.WEEK,
            residents = people(),
            residences = listOf(Residence(home, "家")),
            products = listOf(item(product)),
            stays = listOf(
                stay(1, "2026-05-11", "2026-05-17", setA),
                stay(2, "2026-05-18", null, setB),
            ),
            purchases = listOf(
                purchase(1, date = "2026-05-11", qty = r(7)),
                purchase(2, date = "2026-05-18", qty = r(1)),
            ),
            counts = listOf(count(1, date = "2026-05-17", actual = r(0))),
        )
        val math = project(contrast, today).math(product)!!
        assertEquals(r(1), math.rates.rate(setA))
        assertNull(math.rates.rate(setB))
        assertEquals(Depletion.Unknown, math.forecast.depletion)
        assertEquals(setB, math.forecast.missing)
        assertFalse(math.lowStock)
        val names = formatMissingUsage(residentNames(setB, contrast.residents))
        assertTrue(names.contains("B"))
        assertFalse(names.contains("方案"))
        assertFalse(names.contains("/天"))
    }

    @Test
    fun beyondAYearSaysSo() {
        val today = date("2026-05-11")
        val input = LedgerInput(
            lookback = Lookback.WEEK,
            residents = people(),
            residences = listOf(Residence(home, "家")),
            products = listOf(item(product)),
            stays = listOf(stay(1, "2026-05-04", "2026-05-10", setA)),
            purchases = listOf(purchase(1, date = "2026-05-04", qty = r(400))),
            counts = listOf(count(1, date = "2026-05-10", actual = r(399))),
        )
        val math = project(input, today).math(product)!!
        assertEquals(Depletion.BeyondYear, math.forecast.depletion)
        assertNull(math.forecast.speed)
        assertEquals("一年内不会用完", formatDepletion(math.forecast.depletion, today))
        assertFalse(math.lowStock)
    }

    private fun weekOne(lookback: Lookback = Lookback.WEEK) = LedgerInput(
        lookback = lookback,
        residents = people(),
        residences = listOf(Residence(home, "家")),
        products = listOf(item(product)),
        stays = listOf(
            stay(1, "2026-05-04", "2026-05-08", setA),
            stay(2, "2026-05-09", "2026-05-10", setAb),
        ),
        purchases = listOf(
            purchase(1, date = "2026-05-04", qty = r(2), priceFen = 2000, expiry = "2026-06-30"),
        ),
        counts = listOf(count(1, date = "2026-05-10", actual = r(1))),
    )

    private fun weekTwo(lookback: Lookback) = weekOne(lookback).copy(
        stays = weekOne().stays + stay(3, "2026-05-11", "2026-05-17", setB),
        counts = weekOne().counts + count(2, date = "2026-05-17", actual = r(3, 10)),
    )

    private fun schemeThreeLedger() = LedgerInput(
        lookback = Lookback.MONTH,
        residents = people(),
        residences = listOf(Residence(home, "家")),
        products = listOf(item(product)),
        stays = listOf(
            stay(1, "2026-05-18", "2026-05-22", setA),
            stay(2, "2026-05-23", "2026-05-24", setAb),
            stay(3, "2026-05-25", "2026-05-31", setA),
            stay(4, "2026-06-01", "2026-06-03", setA),
            stay(5, "2026-06-04", "2026-06-07", setAb),
        ),
        purchases = listOf(purchase(1, date = "2026-05-18", qty = r(27, 10))),
        counts = listOf(
            count(1, date = "2026-05-24", actual = r(17, 10)),
            count(2, date = "2026-05-31", actual = r(1)),
        ),
    )

    private fun people() = listOf(Resident(a, "A"), Resident(b, "B"))

    private fun item(id: Long, brand: String = "纸巾", expiryWarnDays: Int = 3) = Product(
        id = id,
        residenceId = home,
        brand = brand,
        spec = "标准",
        unit = "包",
        lowStockDays = 5,
        expiryWarnDays = expiryWarnDays,
    )

    private fun stay(
        id: Long,
        start: String,
        end: String?,
        residents: ResidentSet,
        residenceId: Long = home,
    ) = Stay(id, residenceId, date(start), end?.let(::date), residents)

    private fun purchase(
        id: Long,
        productId: Long = product,
        date: String,
        qty: Rational,
        priceFen: Long = 1000,
        expiry: String? = null,
    ) = Purchase(
        id = id,
        productId = productId,
        date = date(date),
        quantity = qty,
        priceFen = priceFen,
        discountFen = 0,
        channel = "超市",
        expiry = expiry?.let(::date),
    )

    private fun count(id: Long, productId: Long = product, date: String, actual: Rational) =
        Count(id, productId, date(date), actual)

    private fun consumption(id: Long, productId: Long = product, date: String, qty: Rational) =
        Consumption(id, productId, date(date), qty)

    private fun discard(id: Long, productId: Long = product, date: String, qty: Rational) =
        Discard(id, productId, date(date), qty, reason = null)

    private fun date(text: String) = LocalDate.parse(text)

    private fun r(num: Long, den: Long = 1) = Rational.of(num, den)

    private fun List<BatchView>.remainingOf(purchaseId: Long) =
        first { it.sourceId == purchaseId }.remaining

    private fun assertNotEqualRates(actual: Rational?, unexpected: Rational) {
        assertTrue(actual != unexpected)
    }
}
