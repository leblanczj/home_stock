package com.homestock.data

import com.homestock.domain.Consumption
import com.homestock.domain.Count
import com.homestock.domain.Discard
import com.homestock.domain.LedgerInput
import com.homestock.domain.Lookback
import com.homestock.domain.Product
import com.homestock.domain.Purchase
import com.homestock.domain.Rational
import com.homestock.domain.Residence
import com.homestock.domain.Resident
import com.homestock.domain.ResidentSet
import com.homestock.domain.Stay
import java.math.BigInteger
import java.time.LocalDate

data class StoredPhoto(
    val id: Long,
    val purchaseId: Long,
    val relativePath: String,
)

data class Snapshot(
    val residents: List<Resident>,
    val residences: List<Residence>,
    val stays: List<Stay>,
    val products: List<Product>,
    val purchases: List<Purchase>,
    val consumptions: List<Consumption>,
    val discards: List<Discard>,
    val counts: List<Count>,
    val photos: List<StoredPhoto>,
    val lookback: Lookback,
) {
    fun toInput(): LedgerInput = LedgerInput(
        residents = residents,
        residences = residences,
        stays = stays,
        products = products,
        purchases = purchases,
        consumptions = consumptions,
        discards = discards,
        counts = counts,
        lookback = lookback,
    )
}

internal fun ResidentEntity.toDomain() = Resident(id, name, deleted, deletedName, abolished)

internal fun Resident.toEntity() = ResidentEntity(id, name, deleted, deletedName, abolished)

internal fun ResidenceEntity.toDomain() = Residence(id, name, abolished)

internal fun Residence.toEntity() = ResidenceEntity(id, name, abolished)

internal fun StayEntity.toDomain() = Stay(
    id = id,
    residenceId = residenceId,
    start = LocalDate.parse(startDate),
    end = endDate?.let(LocalDate::parse),
    residents = ResidentSet.of(residentIds.split(",").map { it.toLong() }),
    abolished = abolished,
)

internal fun Stay.toEntity() = StayEntity(
    id = id,
    residenceId = residenceId,
    startDate = start.toString(),
    endDate = end?.toString(),
    residentIds = residents.ids.joinToString(","),
    abolished = abolished,
)

internal fun ProductEntity.toDomain() = Product(
    id, residenceId, brand, spec, unit, lowStockDays, expiryWarnDays, abolished,
)

internal fun Product.toEntity() = ProductEntity(
    id, residenceId, brand, spec, unit, lowStockDays, expiryWarnDays, abolished,
)

internal fun PurchaseEntity.toDomain() = Purchase(
    id = id,
    productId = productId,
    date = LocalDate.parse(date),
    quantity = rational(quantityNum, quantityDen),
    priceFen = priceFen,
    discountFen = discountFen,
    channel = channel,
    expiry = expiryDate?.let(LocalDate::parse),
    abolished = abolished,
)

internal fun Purchase.toEntity() = PurchaseEntity(
    id = id,
    productId = productId,
    date = date.toString(),
    quantityNum = quantity.num.toString(),
    quantityDen = quantity.den.toString(),
    priceFen = priceFen,
    discountFen = discountFen,
    channel = channel,
    expiryDate = expiry?.toString(),
    abolished = abolished,
)

internal fun ConsumptionEntity.toDomain() = Consumption(
    id, productId, LocalDate.parse(date), rational(quantityNum, quantityDen), abolished,
)

internal fun Consumption.toEntity() = ConsumptionEntity(
    id, productId, date.toString(), quantity.num.toString(), quantity.den.toString(), abolished,
)

internal fun DiscardEntity.toDomain() = Discard(
    id, productId, LocalDate.parse(date), rational(quantityNum, quantityDen), reason, abolished,
)

internal fun Discard.toEntity() = DiscardEntity(
    id,
    productId,
    date.toString(),
    quantity.num.toString(),
    quantity.den.toString(),
    reason,
    abolished,
)

internal fun CountEntity.toDomain() = Count(
    id, productId, LocalDate.parse(date), rational(actualNum, actualDen), abolished,
)

internal fun Count.toEntity() = CountEntity(
    id, productId, date.toString(), actual.num.toString(), actual.den.toString(), abolished,
)

internal fun PhotoEntity.toDomain() = StoredPhoto(id, purchaseId, relativePath)

private fun rational(num: String, den: String) = Rational.of(BigInteger(num), BigInteger(den))

internal fun nextId(ids: List<Long>): Long = (ids.maxOrNull() ?: 0L) + 1L
