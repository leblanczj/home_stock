package com.homestock.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ResidentEntity::class,
        ResidenceEntity::class,
        StayEntity::class,
        ProductEntity::class,
        PurchaseEntity::class,
        PhotoEntity::class,
        ConsumptionEntity::class,
        DiscardEntity::class,
        CountEntity::class,
        SettingsEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class HomeStockDatabase : RoomDatabase() {
    abstract fun dao(): InventoryDao

    companion object {
        fun create(context: Context): HomeStockDatabase =
            Room.databaseBuilder(context, HomeStockDatabase::class.java, "homestock.db").build()
    }
}
