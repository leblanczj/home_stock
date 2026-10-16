package com.homestock.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "residents")
data class ResidentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val deleted: Boolean = false,
    val deletedName: String? = null,
    val abolished: Boolean = false,
)

@Entity(tableName = "residences")
data class ResidenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val abolished: Boolean = false,
)

@Entity(
    tableName = "stays",
    foreignKeys = [
        ForeignKey(
            entity = ResidenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["residenceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("residenceId")],
)
data class StayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val residenceId: Long,
    val startDate: String,
    val endDate: String?,
    val residentIds: String,
    val abolished: Boolean = false,
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = ResidenceEntity::class,
            parentColumns = ["id"],
            childColumns = ["residenceId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["residenceId", "brand", "spec"], unique = true)],
)
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val residenceId: Long,
    val brand: String,
    val spec: String,
    val unit: String,
    val lowStockDays: Int = 5,
    val expiryWarnDays: Int = 3,
    val abolished: Boolean = false,
)

@Entity(
    tableName = "purchases",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("productId")],
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val date: String,
    val quantityNum: String,
    val quantityDen: String,
    val priceFen: Long,
    val discountFen: Long,
    val channel: String,
    val expiryDate: String?,
    val abolished: Boolean = false,
)

@Entity(
    tableName = "photos",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("purchaseId")],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val purchaseId: Long,
    val relativePath: String,
)

@Entity(
    tableName = "consumptions",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("productId")],
)
data class ConsumptionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val date: String,
    val quantityNum: String,
    val quantityDen: String,
    val abolished: Boolean = false,
)

@Entity(
    tableName = "discards",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("productId")],
)
data class DiscardEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val date: String,
    val quantityNum: String,
    val quantityDen: String,
    val reason: String?,
    val abolished: Boolean = false,
)

@Entity(
    tableName = "counts",
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("productId")],
)
data class CountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val productId: Long,
    val date: String,
    val actualNum: String,
    val actualDen: String,
    val abolished: Boolean = false,
)

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,
    val lookback: String = "WEEK",
)
