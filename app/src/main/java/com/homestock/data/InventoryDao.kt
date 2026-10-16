package com.homestock.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {
    @Query("SELECT * FROM residents ORDER BY id")
    fun observeResidents(): Flow<List<ResidentEntity>>

    @Query("SELECT * FROM residences ORDER BY id")
    fun observeResidences(): Flow<List<ResidenceEntity>>

    @Query("SELECT * FROM stays ORDER BY id")
    fun observeStays(): Flow<List<StayEntity>>

    @Query("SELECT * FROM products ORDER BY id")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM purchases ORDER BY id")
    fun observePurchases(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM photos ORDER BY id")
    fun observePhotos(): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM consumptions ORDER BY id")
    fun observeConsumptions(): Flow<List<ConsumptionEntity>>

    @Query("SELECT * FROM discards ORDER BY id")
    fun observeDiscards(): Flow<List<DiscardEntity>>

    @Query("SELECT * FROM counts ORDER BY id")
    fun observeCounts(): Flow<List<CountEntity>>

    @Query("SELECT * FROM settings WHERE id = 1")
    fun observeSettings(): Flow<List<SettingsEntity>>

    @Query("SELECT * FROM residents ORDER BY id")
    suspend fun residents(): List<ResidentEntity>

    @Query("SELECT * FROM residences ORDER BY id")
    suspend fun residences(): List<ResidenceEntity>

    @Query("SELECT * FROM stays ORDER BY id")
    suspend fun stays(): List<StayEntity>

    @Query("SELECT * FROM products ORDER BY id")
    suspend fun products(): List<ProductEntity>

    @Query("SELECT * FROM purchases ORDER BY id")
    suspend fun purchases(): List<PurchaseEntity>

    @Query("SELECT * FROM photos ORDER BY id")
    suspend fun photos(): List<PhotoEntity>

    @Query("SELECT * FROM consumptions ORDER BY id")
    suspend fun consumptions(): List<ConsumptionEntity>

    @Query("SELECT * FROM discards ORDER BY id")
    suspend fun discards(): List<DiscardEntity>

    @Query("SELECT * FROM counts ORDER BY id")
    suspend fun counts(): List<CountEntity>

    @Query("SELECT * FROM settings WHERE id = 1")
    suspend fun settings(): SettingsEntity?

    @Insert
    suspend fun insertResident(entity: ResidentEntity): Long

    @Update
    suspend fun updateResident(entity: ResidentEntity)

    @Insert
    suspend fun insertResidence(entity: ResidenceEntity): Long

    @Update
    suspend fun updateResidence(entity: ResidenceEntity)

    @Query("DELETE FROM residences WHERE id = :id")
    suspend fun deleteResidence(id: Long)

    @Insert
    suspend fun insertStay(entity: StayEntity): Long

    @Update
    suspend fun updateStay(entity: StayEntity)

    @Query("DELETE FROM stays WHERE id = :id")
    suspend fun deleteStay(id: Long)

    @Insert
    suspend fun insertProduct(entity: ProductEntity): Long

    @Update
    suspend fun updateProduct(entity: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProduct(id: Long)

    @Insert
    suspend fun insertPurchase(entity: PurchaseEntity): Long

    @Update
    suspend fun updatePurchase(entity: PurchaseEntity)

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun deletePurchase(id: Long)

    @Insert
    suspend fun insertPhoto(entity: PhotoEntity): Long

    @Query("DELETE FROM photos WHERE id = :id")
    suspend fun deletePhoto(id: Long)

    @Insert
    suspend fun insertConsumption(entity: ConsumptionEntity): Long

    @Update
    suspend fun updateConsumption(entity: ConsumptionEntity)

    @Query("DELETE FROM consumptions WHERE id = :id")
    suspend fun deleteConsumption(id: Long)

    @Insert
    suspend fun insertDiscard(entity: DiscardEntity): Long

    @Update
    suspend fun updateDiscard(entity: DiscardEntity)

    @Query("DELETE FROM discards WHERE id = :id")
    suspend fun deleteDiscard(id: Long)

    @Insert
    suspend fun insertCount(entity: CountEntity): Long

    @Update
    suspend fun updateCount(entity: CountEntity)

    @Query("DELETE FROM counts WHERE id = :id")
    suspend fun deleteCount(id: Long)

    @Insert
    suspend fun insertSettings(entity: SettingsEntity)

    @Update
    suspend fun updateSettings(entity: SettingsEntity)
}
