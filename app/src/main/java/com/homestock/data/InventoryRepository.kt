package com.homestock.data

import androidx.room.withTransaction
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
import com.homestock.domain.Stay
import com.homestock.domain.firstProblem
import com.homestock.domain.planResidenceDeletion
import com.homestock.domain.problemMessage
import com.homestock.domain.stayConflict
import com.homestock.domain.validateActual
import com.homestock.domain.validatePurchase
import com.homestock.domain.validateQuantity
import com.homestock.domain.validateStayFields
import com.homestock.domain.validateWarnDays
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class InventoryException(message: String) : Exception(message)

data class PurchaseDraft(
    val id: Long?,
    val productId: Long?,
    val residenceId: Long,
    val brand: String,
    val spec: String,
    val unit: String,
    val date: LocalDate,
    val quantity: Rational,
    val priceFen: Long,
    val discountFen: Long,
    val channel: String,
    val expiry: LocalDate?,
    val newPhotos: List<ByteArray>,
    val removePhotoIds: Set<Long>,
)

class InventoryRepository(
    private val db: HomeStockDatabase,
    private val photos: PhotoFiles,
) {
    private val dao = db.dao()

    fun observe(): Flow<Snapshot> {
        val household = combine(
            dao.observeResidents(),
            dao.observeResidences(),
            dao.observeStays(),
            dao.observeProducts(),
            dao.observeSettings(),
        ) { residents, residences, stays, products, settings ->
            Household(residents, residences, stays, products, settings.firstOrNull())
        }
        val ledger = combine(
            dao.observePurchases(),
            dao.observePhotos(),
            dao.observeConsumptions(),
            dao.observeDiscards(),
            dao.observeCounts(),
        ) { purchases, photoRows, consumptions, discards, counts ->
            LedgerRows(purchases, photoRows, consumptions, discards, counts)
        }
        return combine(household, ledger) { home, rows -> home.snapshot(rows) }
    }

    fun photoFile(relativePath: String) = photos.file(relativePath)

    suspend fun addResident(name: String): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return failure("请填写居住者的名字。")
        dao.insertResident(ResidentEntity(name = trimmed))
        return ok()
    }

    suspend fun renameResident(id: Long, name: String): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return failure("请填写居住者的名字。")
        val resident = load().residents.find { it.id == id } ?: return failure("找不到这个居住者。")
        if (resident.deleted) return failure("已删除的居住者不能改名。")
        dao.updateResident(resident.copy(name = trimmed).toEntity())
        return ok()
    }

    suspend fun setResidentAbolished(id: Long, abolished: Boolean): Result<Unit> {
        val resident = load().residents.find { it.id == id } ?: return failure("找不到这个居住者。")
        if (resident.deleted) return failure("已删除的居住者不能再改。")
        dao.updateResident(resident.copy(abolished = abolished).toEntity())
        return ok()
    }

    suspend fun deleteResident(id: Long): Result<Unit> {
        val resident = load().residents.find { it.id == id } ?: return failure("找不到这个居住者。")
        if (resident.deleted) return ok()
        dao.updateResident(resident.markDeleted().toEntity())
        return ok()
    }

    suspend fun addResidence(name: String): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return failure("请填写住所名称。")
        dao.insertResidence(ResidenceEntity(name = trimmed))
        return ok()
    }

    suspend fun renameResidence(id: Long, name: String): Result<Unit> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return failure("请填写住所名称。")
        val residence = load().residences.find { it.id == id } ?: return failure("找不到这个住所。")
        dao.updateResidence(residence.copy(name = trimmed).toEntity())
        return ok()
    }

    suspend fun setResidenceAbolished(id: Long, abolished: Boolean): Result<Unit> {
        val snapshot = load()
        val residence = snapshot.residences.find { it.id == id } ?: return failure("找不到这个住所。")
        val next = snapshot.toInput().copy(
            residences = snapshot.residences.map { if (it.id == id) it.copy(abolished = abolished) else it },
        )
        return gate(next, checkStays = !abolished, checkStock = !abolished) {
            dao.updateResidence(residence.copy(abolished = abolished).toEntity())
        }
    }

    suspend fun deleteResidence(id: Long): Result<Unit> {
        val snapshot = load()
        if (snapshot.residences.none { it.id == id }) return failure("找不到这个住所。")
        val productIds = snapshot.products.filter { it.residenceId == id }.map { it.id }.toSet()
        val purchaseIds = snapshot.purchases.filter { it.productId in productIds }.map { it.id }
        db.withTransaction { dao.deleteResidence(id) }
        purchaseIds.forEach { photos.deletePurchase(it) }
        return ok()
    }

    fun deletionPlan(snapshot: Snapshot, residenceId: Long) = planResidenceDeletion(
        snapshot.toInput(),
        residenceId,
        snapshot.photos.count { photo ->
            val purchase = snapshot.purchases.find { it.id == photo.purchaseId }
            val product = snapshot.products.find { it.id == purchase?.productId }
            product?.residenceId == residenceId
        },
    )

    suspend fun saveStay(stay: Stay): Result<Unit> {
        validateStayFields(stay)?.let { return failure(it) }
        val snapshot = load()
        val previous = snapshot.stays.find { it.id == stay.id }
        val blocked = stay.residents.ids.filter { personId ->
            val person = snapshot.residents.find { it.id == personId } ?: return failure("找不到居住者。")
            (person.deleted || person.abolished) && personId !in previous?.residents?.ids.orEmpty()
        }
        if (blocked.isNotEmpty()) return failure("已删除或已废除的居住者不能选进新的居住行。")
        val nextStay = stay.copy(abolished = previous?.abolished ?: stay.abolished)
        val stays = snapshot.stays.filter { it.id != stay.id } + nextStay
        return gate(snapshot.toInput().copy(stays = stays), checkStays = true, checkStock = false) {
            if (stay.id == 0L) dao.insertStay(nextStay.toEntity()) else dao.updateStay(nextStay.toEntity())
        }
    }

    suspend fun setStayAbolished(id: Long, abolished: Boolean): Result<Unit> {
        val snapshot = load()
        val stay = snapshot.stays.find { it.id == id } ?: return failure("找不到这条居住记录。")
        val updated = stay.copy(abolished = abolished)
        val stays = snapshot.stays.map { if (it.id == id) updated else it }
        return gate(snapshot.toInput().copy(stays = stays), checkStays = !abolished, checkStock = false) {
            dao.updateStay(updated.toEntity())
        }
    }

    suspend fun deleteStay(id: Long): Result<Unit> {
        dao.deleteStay(id)
        return ok()
    }

    suspend fun updateProduct(product: Product): Result<Unit> {
        validateWarnDays(product.lowStockDays)?.let { return failure(it) }
        validateWarnDays(product.expiryWarnDays)?.let { return failure(it) }
        val brand = product.brand.trim()
        val spec = product.spec.trim()
        val unit = product.unit.trim()
        if (brand.isEmpty() || spec.isEmpty() || unit.isEmpty()) return failure("品牌、规格和单位都不能空。")
        val snapshot = load()
        val current = snapshot.products.find { it.id == product.id } ?: return failure("找不到这个用品。")
        val clash = snapshot.products.any {
            it.id != product.id && it.residenceId == current.residenceId && it.brand == brand && it.spec == spec
        }
        if (clash) return failure("这个住所已经有相同的品牌和规格。")
        val updated = current.copy(
            brand = brand,
            spec = spec,
            unit = unit,
            lowStockDays = product.lowStockDays,
            expiryWarnDays = product.expiryWarnDays,
        )
        dao.updateProduct(updated.toEntity())
        return ok()
    }

    suspend fun setProductAbolished(id: Long, abolished: Boolean): Result<Unit> {
        val snapshot = load()
        val product = snapshot.products.find { it.id == id } ?: return failure("找不到这个用品。")
        val updated = product.copy(abolished = abolished)
        val next = snapshot.toInput().copy(
            products = snapshot.products.map { if (it.id == id) updated else it },
        )
        return gate(next, checkStays = false, checkStock = true) {
            dao.updateProduct(updated.toEntity())
        }
    }

    suspend fun deleteProduct(id: Long): Result<Unit> {
        val snapshot = load()
        val purchaseIds = snapshot.purchases.filter { it.productId == id }.map { it.id }
        db.withTransaction { dao.deleteProduct(id) }
        purchaseIds.forEach { photos.deletePurchase(it) }
        return ok()
    }

    suspend fun savePurchase(draft: PurchaseDraft): Result<Unit> {
        val channel = draft.channel.trim()
        validatePurchase(draft.quantity, draft.priceFen, draft.discountFen, channel)?.let { return failure(it) }
        val brand = draft.brand.trim()
        val spec = draft.spec.trim()
        val unit = draft.unit.trim()
        if (brand.isEmpty() || spec.isEmpty()) return failure("请填写品牌和规格。")
        val snapshot = load()
        val existing = if (draft.productId != null) {
            snapshot.products.find { it.id == draft.productId } ?: return failure("找不到这个用品。")
        } else {
            snapshot.products.find { it.residenceId == draft.residenceId && it.brand == brand && it.spec == spec }
        }
        if (existing == null && unit.isEmpty()) return failure("请填写单位。")
        val residenceOk = snapshot.residences.any { it.id == draft.residenceId && !it.abolished }
        if (existing == null && !residenceOk) return failure("请先选择一个住所。")
        val product = existing ?: Product(
            id = nextId(snapshot.products.map { it.id }),
            residenceId = draft.residenceId,
            brand = brand,
            spec = spec,
            unit = unit,
        )
        val previous = snapshot.purchases.find { it.id == draft.id }
        val purchaseId = draft.id ?: nextId(snapshot.purchases.map { it.id })
        val purchase = Purchase(
            id = purchaseId,
            productId = product.id,
            date = draft.date,
            quantity = draft.quantity,
            priceFen = draft.priceFen,
            discountFen = draft.discountFen,
            channel = channel,
            expiry = draft.expiry,
            abolished = previous?.abolished ?: false,
        )
        val products = if (existing == null) snapshot.products + product else snapshot.products
        val purchases = snapshot.purchases.filter { it.id != purchase.id } + purchase
        val next = snapshot.toInput().copy(products = products, purchases = purchases)
        return gate(next, checkStays = false, checkStock = true) {
            if (existing == null) dao.insertProduct(product.toEntity())
            if (draft.id == null) dao.insertPurchase(purchase.toEntity()) else dao.updatePurchase(purchase.toEntity())
            draft.removePhotoIds.forEach { photoId ->
                val photo = snapshot.photos.find { it.id == photoId } ?: return@forEach
                dao.deletePhoto(photoId)
                photos.deleteRelative(photo.relativePath)
            }
            draft.newPhotos.forEach { bytes ->
                val relative = photos.write(purchase.id, bytes)
                dao.insertPhoto(PhotoEntity(purchaseId = purchase.id, relativePath = relative))
            }
        }
    }

    suspend fun setPurchaseAbolished(id: Long, abolished: Boolean): Result<Unit> {
        val snapshot = load()
        val purchase = snapshot.purchases.find { it.id == id } ?: return failure("找不到这笔新购。")
        val updated = purchase.copy(abolished = abolished)
        val next = snapshot.toInput().copy(
            purchases = snapshot.purchases.map { if (it.id == id) updated else it },
        )
        return gate(next, checkStays = false, checkStock = true) {
            dao.updatePurchase(updated.toEntity())
        }
    }

    suspend fun deletePurchase(id: Long): Result<Unit> {
        val snapshot = load()
        if (snapshot.purchases.none { it.id == id }) return failure("找不到这笔新购。")
        val next = snapshot.toInput().copy(purchases = snapshot.purchases.filter { it.id != id })
        return gate(next, checkStays = false, checkStock = true) {
            dao.deletePurchase(id)
            photos.deletePurchase(id)
        }
    }

    suspend fun saveCount(id: Long?, productId: Long, date: LocalDate, actual: Rational): Result<Unit> {
        validateActual(actual)?.let { return failure(it) }
        val snapshot = load()
        if (snapshot.products.none { it.id == productId }) return failure("找不到这个用品。")
        val previous = snapshot.counts.find { it.id == id }
        val countId = id ?: nextId(snapshot.counts.map { it.id })
        val count = Count(countId, productId, date, actual, abolished = previous?.abolished ?: false)
        val next = snapshot.toInput().copy(counts = snapshot.counts.filter { it.id != countId } + count)
        return gate(next, checkStays = false, checkStock = true) {
            if (id == null) dao.insertCount(count.toEntity()) else dao.updateCount(count.toEntity())
        }
    }

    suspend fun setCountAbolished(id: Long, abolished: Boolean): Result<Unit> =
        toggleCount(id, abolished)

    suspend fun deleteCount(id: Long): Result<Unit> {
        val snapshot = load()
        val next = snapshot.toInput().copy(counts = snapshot.counts.filter { it.id != id })
        return gate(next, checkStays = false, checkStock = true) { dao.deleteCount(id) }
    }

    suspend fun saveConsumption(id: Long?, productId: Long, date: LocalDate, quantity: Rational): Result<Unit> {
        validateQuantity(quantity, "消耗数量")?.let { return failure(it) }
        val snapshot = load()
        if (snapshot.products.none { it.id == productId }) return failure("找不到这个用品。")
        val previous = snapshot.consumptions.find { it.id == id }
        val entryId = id ?: nextId(snapshot.consumptions.map { it.id })
        val entry = Consumption(entryId, productId, date, quantity, abolished = previous?.abolished ?: false)
        val next = snapshot.toInput().copy(
            consumptions = snapshot.consumptions.filter { it.id != entryId } + entry,
        )
        return gate(next, checkStays = false, checkStock = true) {
            if (id == null) dao.insertConsumption(entry.toEntity()) else dao.updateConsumption(entry.toEntity())
        }
    }

    suspend fun setConsumptionAbolished(id: Long, abolished: Boolean): Result<Unit> {
        val snapshot = load()
        val entry = snapshot.consumptions.find { it.id == id } ?: return failure("找不到这笔消耗。")
        val updated = entry.copy(abolished = abolished)
        val next = snapshot.toInput().copy(
            consumptions = snapshot.consumptions.map { if (it.id == id) updated else it },
        )
        return gate(next, checkStays = false, checkStock = true) { dao.updateConsumption(updated.toEntity()) }
    }

    suspend fun deleteConsumption(id: Long): Result<Unit> {
        val snapshot = load()
        val next = snapshot.toInput().copy(consumptions = snapshot.consumptions.filter { it.id != id })
        return gate(next, checkStays = false, checkStock = true) { dao.deleteConsumption(id) }
    }

    suspend fun saveDiscard(
        id: Long?,
        productId: Long,
        date: LocalDate,
        quantity: Rational,
        reason: String?,
    ): Result<Unit> {
        validateQuantity(quantity, "废弃数量")?.let { return failure(it) }
        val snapshot = load()
        if (snapshot.products.none { it.id == productId }) return failure("找不到这个用品。")
        val previous = snapshot.discards.find { it.id == id }
        val entryId = id ?: nextId(snapshot.discards.map { it.id })
        val entry = Discard(
            entryId,
            productId,
            date,
            quantity,
            reason?.trim()?.ifEmpty { null },
            abolished = previous?.abolished ?: false,
        )
        val next = snapshot.toInput().copy(discards = snapshot.discards.filter { it.id != entryId } + entry)
        return gate(next, checkStays = false, checkStock = true) {
            if (id == null) dao.insertDiscard(entry.toEntity()) else dao.updateDiscard(entry.toEntity())
        }
    }

    suspend fun setDiscardAbolished(id: Long, abolished: Boolean): Result<Unit> {
        val snapshot = load()
        val entry = snapshot.discards.find { it.id == id } ?: return failure("找不到这笔废弃。")
        val updated = entry.copy(abolished = abolished)
        val next = snapshot.toInput().copy(discards = snapshot.discards.map { if (it.id == id) updated else it })
        return gate(next, checkStays = false, checkStock = true) { dao.updateDiscard(updated.toEntity()) }
    }

    suspend fun deleteDiscard(id: Long): Result<Unit> {
        val snapshot = load()
        val next = snapshot.toInput().copy(discards = snapshot.discards.filter { it.id != id })
        return gate(next, checkStays = false, checkStock = true) { dao.deleteDiscard(id) }
    }

    suspend fun setLookback(lookback: Lookback) {
        val current = dao.settings()
        if (current == null) dao.insertSettings(SettingsEntity(lookback = lookback.name))
        else dao.updateSettings(current.copy(lookback = lookback.name))
    }

    private suspend fun toggleCount(id: Long, abolished: Boolean): Result<Unit> {
        val snapshot = load()
        val entry = snapshot.counts.find { it.id == id } ?: return failure("找不到这次点数。")
        val updated = entry.copy(abolished = abolished)
        val next = snapshot.toInput().copy(counts = snapshot.counts.map { if (it.id == id) updated else it })
        return gate(next, checkStays = false, checkStock = true) { dao.updateCount(updated.toEntity()) }
    }

    private suspend fun gate(
        next: LedgerInput,
        checkStays: Boolean,
        checkStock: Boolean,
        write: suspend () -> Unit,
    ): Result<Unit> {
        if (checkStays) {
            stayConflict(next)?.let { return failure(it.message) }
        }
        if (checkStock) {
            firstProblem(next)?.let { return failure(problemMessage(it, next)) }
        }
        db.withTransaction { write() }
        return ok()
    }

    private suspend fun load(): Snapshot {
        val settings = dao.settings()
        return Snapshot(
            residents = dao.residents().map { it.toDomain() },
            residences = dao.residences().map { it.toDomain() },
            stays = dao.stays().map { it.toDomain() },
            products = dao.products().map { it.toDomain() },
            purchases = dao.purchases().map { it.toDomain() },
            consumptions = dao.consumptions().map { it.toDomain() },
            discards = dao.discards().map { it.toDomain() },
            counts = dao.counts().map { it.toDomain() },
            photos = dao.photos().map { it.toDomain() },
            lookback = settings?.lookback?.let { runCatching { Lookback.valueOf(it) }.getOrNull() } ?: Lookback.WEEK,
        )
    }

    private fun ok() = Result.success(Unit)

    private fun failure(message: String) = Result.failure<Unit>(InventoryException(message))
}

private data class Household(
    val residents: List<ResidentEntity>,
    val residences: List<ResidenceEntity>,
    val stays: List<StayEntity>,
    val products: List<ProductEntity>,
    val settings: SettingsEntity?,
) {
    fun snapshot(rows: LedgerRows) = Snapshot(
        residents = residents.map { it.toDomain() },
        residences = residences.map { it.toDomain() },
        stays = stays.map { it.toDomain() },
        products = products.map { it.toDomain() },
        purchases = rows.purchases.map { it.toDomain() },
        consumptions = rows.consumptions.map { it.toDomain() },
        discards = rows.discards.map { it.toDomain() },
        counts = rows.counts.map { it.toDomain() },
        photos = rows.photos.map { it.toDomain() },
        lookback = settings?.lookback?.let { runCatching { Lookback.valueOf(it) }.getOrNull() } ?: Lookback.WEEK,
    )
}

private data class LedgerRows(
    val purchases: List<PurchaseEntity>,
    val photos: List<PhotoEntity>,
    val consumptions: List<ConsumptionEntity>,
    val discards: List<DiscardEntity>,
    val counts: List<CountEntity>,
)
