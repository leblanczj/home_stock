package com.homestock.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.comparisons.compareBy

data class ProductMath(
    val productId: Long,
    val residenceId: Long,
    val brand: String,
    val spec: String,
    val unit: String,
    val lowStockDays: Int,
    val expiryWarnDays: Int,
    val active: Boolean,
    val onHand: Rational,
    val batches: List<BatchView>,
    val forecast: Forecast,
    val rates: RateModel,
    val counts: Map<Long, CountMath>,
    val pricePoints: List<PricePoint>,
    val lowStock: Boolean,
    val problem: ReplayProblem? = null,
) {
    fun homeRow(): HomeRow = HomeRow(
        productId = productId,
        residenceId = residenceId,
        brand = brand,
        spec = spec,
        unit = unit,
        onHand = onHand,
        speed = forecast.speed,
        depletion = forecast.depletion,
        lowStockDays = lowStockDays,
    )
}

data class Projection(val products: Map<Long, ProductMath>) {
    fun math(productId: Long): ProductMath? = products[productId]
}

fun project(input: LedgerInput, today: LocalDate): Projection {
    val closed = abolishedResidenceIds(input)
    val window = lookbackWindow(today, input.lookback)
    val products = input.products.associate { product ->
        val active = !product.abolished && product.residenceId !in closed
        product.id to if (!active) {
            inactive(product)
        } else {
            when (val outcome = replayProduct(input, product)) {
                is ReplayOutcome.Failed -> inactive(product).copy(problem = outcome.problem, active = true)
                is ReplayOutcome.Ok -> {
                    val replay = outcome.replay
                    val observations = replay.observations.filter { it.date in window }
                    val explicit = replay.explicit.filter { it.date in window }
                    val rates = computeRates(observations, explicit)
                    val forecast = forecast(
                        onHand = replay.onHand,
                        today = today,
                        rateOf = { rates.rate(it) },
                        setOn = { date -> occupancy(product.residenceId, input, today, date).set },
                    )
                    ProductMath(
                        productId = product.id,
                        residenceId = product.residenceId,
                        brand = product.brand,
                        spec = product.spec,
                        unit = product.unit,
                        lowStockDays = product.lowStockDays,
                        expiryWarnDays = product.expiryWarnDays,
                        active = true,
                        onHand = replay.onHand,
                        batches = replay.batches,
                        forecast = forecast,
                        rates = rates,
                        counts = replay.counts,
                        pricePoints = replay.pricePoints,
                        lowStock = isLowStock(replay.onHand, forecast.depletion, today, product.lowStockDays),
                    )
                }
            }
        }
    }
    return Projection(products)
}

private fun inactive(product: Product) = ProductMath(
    productId = product.id,
    residenceId = product.residenceId,
    brand = product.brand,
    spec = product.spec,
    unit = product.unit,
    lowStockDays = product.lowStockDays,
    expiryWarnDays = product.expiryWarnDays,
    active = false,
    onHand = Rational.ZERO,
    batches = emptyList(),
    forecast = Forecast(Depletion.Unknown, null),
    rates = RateModel(),
    counts = emptyMap(),
    pricePoints = emptyList(),
    lowStock = false,
)

fun Projection.lowStockRows(residenceId: Long): List<HomeRow> =
    products.values
        .filter { it.active && it.residenceId == residenceId && it.lowStock && it.problem == null }
        .sortedWith(
            compareBy<ProductMath> { !it.onHand.isZero() }
                .thenBy { row ->
                    when (val depletion = row.forecast.depletion) {
                        is Depletion.At -> depletion.days
                        else -> Rational.ZERO
                    }
                }
                .thenBy { it.brand }
                .thenBy { it.spec },
        )
        .map { it.homeRow() }

fun Projection.expiryRows(residenceId: Long, today: LocalDate): List<ExpiryRow> =
    products.values
        .filter { it.active && it.residenceId == residenceId }
        .flatMap { math ->
            math.batches.mapNotNull { batch ->
                if (!isExpiring(batch.remaining, batch.expiry, today, math.expiryWarnDays)) return@mapNotNull null
                val expiry = batch.expiry ?: return@mapNotNull null
                ExpiryRow(
                    productId = math.productId,
                    residenceId = math.residenceId,
                    brand = math.brand,
                    spec = math.spec,
                    unit = math.unit,
                    remaining = batch.remaining,
                    expiry = expiry,
                    channel = batch.channel,
                    warnDays = math.expiryWarnDays,
                    daysFromToday = ChronoUnit.DAYS.between(today, expiry).toInt(),
                )
            }
        }
        .sortedWith(compareBy<ExpiryRow> { it.daysFromToday >= 0 }.thenBy { it.expiry }.thenBy { it.brand })

sealed class CountPreview {
    data class Ready(val math: CountMath) : CountPreview()
    data class Rejected(val problem: ReplayProblem) : CountPreview()
}

fun previewCount(
    input: LedgerInput,
    productId: Long,
    date: LocalDate,
    actual: Rational,
    editingId: Long?,
): CountPreview {
    val product = input.products.firstOrNull { it.id == productId }
        ?: return CountPreview.Rejected(ReplayProblem(productId, ProblemKind.CONSUMPTION, editingId ?: 0, date))
    val id = editingId ?: ((input.counts.maxOfOrNull { it.id } ?: 0L) + 1L)
    val draft = Count(id = id, productId = productId, date = date, actual = actual)
    val next = input.copy(counts = input.counts.filter { it.id != id } + draft)
    return when (val outcome = replayProduct(next, product)) {
        is ReplayOutcome.Failed -> CountPreview.Rejected(outcome.problem)
        is ReplayOutcome.Ok -> {
            val math = outcome.replay.counts[id]
            if (math == null) CountPreview.Rejected(ReplayProblem(productId, ProblemKind.CONSUMPTION, id, date))
            else CountPreview.Ready(math)
        }
    }
}

fun stayConflict(input: LedgerInput): StayConflict? {
    val closed = abolishedResidenceIds(input)
    val active = input.stays.filter { !it.abolished && it.residenceId !in closed }
    for (i in active.indices) {
        for (j in i + 1 until active.size) {
            val found = pairConflict(active[i], active[j], input) ?: continue
            return found
        }
    }
    return null
}

fun validateStayFields(stay: Stay): String? {
    if (stay.end != null && stay.end < stay.start) return "结束日不能早于开始日。"
    return null
}

private fun pairConflict(a: Stay, b: Stay, input: LedgerInput): StayConflict? {
    if (!intervalsOverlap(a, b)) return null
    val left = describeStay(a, input)
    val right = describeStay(b, input)
    if (a.residenceId == b.residenceId) {
        return StayConflict(
            a.id,
            b.id,
            "同一住所的居住日期重叠：$left 与 $right。请改其中一行后再保存。",
        )
    }
    val shared = a.residents.ids.toSet().intersect(b.residents.ids.toSet())
    if (shared.isNotEmpty()) {
        return StayConflict(
            a.id,
            b.id,
            "同一个人不能在同一天出现在两个住所：$left 与 $right。请改其中一行后再保存。",
        )
    }
    return null
}

private fun intervalsOverlap(a: Stay, b: Stay): Boolean {
    val aBeforeBEnds = b.end == null || a.start <= b.end
    val bBeforeAEnds = a.end == null || b.start <= a.end
    return aBeforeBEnds && bBeforeAEnds
}

private fun describeStay(stay: Stay, input: LedgerInput): String {
    val home = input.residences.find { it.id == stay.residenceId }?.name ?: "住所"
    val people = stay.residents.ids.joinToString("、") { id ->
        input.residents.find { it.id == id }?.displayName() ?: "已删除"
    }
    val end = stay.end?.toString() ?: "至今"
    return "$home $people ${stay.start} 至 $end"
}

fun validatePurchase(quantity: Rational, priceFen: Long, discountFen: Long, channel: String): String? {
    if (quantity <= Rational.ZERO) return "数量必须大于 0。"
    if (priceFen < 0) return "购买价格不能小于 0。"
    if (discountFen < 0) return "优惠不能小于 0。"
    if (discountFen > priceFen) return "优惠不能大于购买价格。"
    if (channel.trim().isEmpty()) return "请填写渠道。"
    return null
}

fun validateQuantity(quantity: Rational, label: String): String? =
    if (quantity <= Rational.ZERO) "${label}必须大于 0。" else null

fun validateActual(actual: Rational): String? =
    if (actual < Rational.ZERO) "实盘不能小于 0。" else null

fun validateWarnDays(days: Int): String? =
    if (days !in 0..365) "提醒天数要在 0 到 365 之间。" else null

fun problemMessage(problem: ReplayProblem, input: LedgerInput): String {
    val product = input.products.find { it.id == problem.productId }
    val label = product?.let { "${it.brand} ${it.spec}" } ?: "用品"
    val kind = if (problem.kind == ProblemKind.CONSUMPTION) "消耗" else "废弃"
    return "${problem.date} 的$kind（$label）超过当时的在手，这次没有保存。"
}

data class ResidenceDeletionPlan(
    val residenceName: String,
    val products: Int,
    val batches: Int,
    val photos: Int,
    val stays: Int,
) {
    fun message(): String =
        "将永久删除住所「$residenceName」里的 $products 个用品、$batches 个批次、$photos 张截图和 $stays 条居住记录，不会转移到其他住所。居住者名单会保留。"
}

fun planResidenceDeletion(input: LedgerInput, residenceId: Long, photoCount: Int): ResidenceDeletionPlan? {
    val residence = input.residences.find { it.id == residenceId } ?: return null
    val productIds = input.products.filter { it.residenceId == residenceId }.map { it.id }.toSet()
    val batches = input.purchases.count { it.productId in productIds }
    val stays = input.stays.count { it.residenceId == residenceId }
    return ResidenceDeletionPlan(residence.name, productIds.size, batches, photoCount, stays)
}
