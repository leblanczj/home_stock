package com.homestock.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.homestock.HomeStockApp
import com.homestock.data.InventoryRepository
import com.homestock.data.PurchaseDraft
import com.homestock.data.Snapshot
import com.homestock.data.StoredPhoto
import com.homestock.domain.Consumption
import com.homestock.domain.Count
import com.homestock.domain.Discard
import com.homestock.domain.LedgerInput
import com.homestock.domain.Lookback
import com.homestock.domain.Product
import com.homestock.domain.Projection
import com.homestock.domain.Purchase
import com.homestock.domain.Rational
import com.homestock.domain.Stay
import com.homestock.domain.project
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UiState(
    val ready: Boolean = false,
    val input: LedgerInput = LedgerInput(),
    val photos: List<StoredPhoto> = emptyList(),
    val projection: Projection = Projection(emptyMap()),
    val today: LocalDate = LocalDate.now(),
    val residenceId: Long? = null,
)

class InventoryViewModel(app: Application) : AndroidViewModel(app) {
    private val repository: InventoryRepository = (app as HomeStockApp).repository
    private val selected = MutableStateFlow<Long?>(null)
    private val messagesFlow = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = messagesFlow.asSharedFlow()

    val state: StateFlow<UiState> = combine(repository.observe(), selected) { snapshot, residenceId ->
        val today = LocalDate.now()
        val input = snapshot.toInput()
        val active = snapshot.residences.filter { !it.abolished }
        val chosen = residenceId?.takeIf { id -> active.any { it.id == id } } ?: active.firstOrNull()?.id
        UiState(
            ready = true,
            input = input,
            photos = snapshot.photos,
            projection = project(input, today),
            today = today,
            residenceId = chosen,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun selectResidence(id: Long) {
        selected.value = id
    }

    fun setLookback(lookback: Lookback) {
        viewModelScope.launch { repository.setLookback(lookback) }
    }

    fun photoFile(relativePath: String): File = repository.photoFile(relativePath)

    fun addResident(name: String) = launch { addResident(name) }

    fun renameResident(id: Long, name: String) = launch { renameResident(id, name) }

    fun setResidentAbolished(id: Long, abolished: Boolean) = launch { setResidentAbolished(id, abolished) }

    fun deleteResident(id: Long) = launch { deleteResident(id) }

    fun addResidence(name: String) = launch { addResidence(name) }

    fun renameResidence(id: Long, name: String) = launch { renameResidence(id, name) }

    fun setResidenceAbolished(id: Long, abolished: Boolean) = launch { setResidenceAbolished(id, abolished) }

    fun deleteResidence(id: Long) = launch { deleteResidence(id) }

    fun saveStay(stay: Stay, onDone: (Boolean) -> Unit) = launch(onDone) { saveStay(stay) }

    fun setStayAbolished(id: Long, abolished: Boolean) = launch { setStayAbolished(id, abolished) }

    fun deleteStay(id: Long) = launch { deleteStay(id) }

    fun updateProduct(product: Product) = launch { updateProduct(product) }

    fun setProductAbolished(id: Long, abolished: Boolean) = launch { setProductAbolished(id, abolished) }

    fun deleteProduct(id: Long, onDone: (Boolean) -> Unit = {}) = launch(onDone) { deleteProduct(id) }

    fun savePurchase(draft: PurchaseDraft, uris: List<Uri>, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val bytes = mutableListOf<ByteArray>()
            for (uri in uris) {
                val data = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (data == null) {
                    messagesFlow.tryEmit("有截图没有读出来。")
                    onDone(false)
                    return@launch
                }
                bytes += data
            }
            finish(repository.savePurchase(draft.copy(newPhotos = bytes)), onDone)
        }
    }

    fun setPurchaseAbolished(id: Long, abolished: Boolean) = launch { setPurchaseAbolished(id, abolished) }

    fun deletePurchase(id: Long) = launch { deletePurchase(id) }

    fun saveCount(id: Long?, productId: Long, date: LocalDate, actual: Rational, onDone: (Boolean) -> Unit) =
        launch(onDone) { saveCount(id, productId, date, actual) }

    fun setCountAbolished(id: Long, abolished: Boolean) = launch { setCountAbolished(id, abolished) }

    fun deleteCount(id: Long) = launch { deleteCount(id) }

    fun saveConsumption(
        id: Long?,
        productId: Long,
        date: LocalDate,
        quantity: Rational,
        onDone: (Boolean) -> Unit,
    ) = launch(onDone) { saveConsumption(id, productId, date, quantity) }

    fun setConsumptionAbolished(id: Long, abolished: Boolean) = launch { setConsumptionAbolished(id, abolished) }

    fun deleteConsumption(id: Long) = launch { deleteConsumption(id) }

    fun saveDiscard(
        id: Long?,
        productId: Long,
        date: LocalDate,
        quantity: Rational,
        reason: String?,
        onDone: (Boolean) -> Unit,
    ) = launch(onDone) { saveDiscard(id, productId, date, quantity, reason) }

    fun setDiscardAbolished(id: Long, abolished: Boolean) = launch { setDiscardAbolished(id, abolished) }

    fun deleteDiscard(id: Long) = launch { deleteDiscard(id) }

    fun snapshot(): Snapshot {
        val current = state.value
        return Snapshot(
            residents = current.input.residents,
            residences = current.input.residences,
            stays = current.input.stays,
            products = current.input.products,
            purchases = current.input.purchases,
            consumptions = current.input.consumptions,
            discards = current.input.discards,
            counts = current.input.counts,
            photos = current.photos,
            lookback = current.input.lookback,
        )
    }

    private fun launch(block: suspend InventoryRepository.() -> Result<Unit>) {
        viewModelScope.launch { finish(repository.block(), {}) }
    }

    private fun launch(onDone: (Boolean) -> Unit, block: suspend InventoryRepository.() -> Result<Unit>) {
        viewModelScope.launch { finish(repository.block(), onDone) }
    }

    private suspend fun finish(result: Result<Unit>, onDone: (Boolean) -> Unit) {
        result.exceptionOrNull()?.message?.let { messagesFlow.emit(it) }
        onDone(result.isSuccess)
    }
}

internal fun UiState.productPurchases(productId: Long): List<Purchase> =
    input.purchases.filter { it.productId == productId }

internal fun UiState.productCounts(productId: Long): List<Count> =
    input.counts.filter { it.productId == productId }

internal fun UiState.productConsumptions(productId: Long): List<Consumption> =
    input.consumptions.filter { it.productId == productId }

internal fun UiState.productDiscards(productId: Long): List<Discard> =
    input.discards.filter { it.productId == productId }
