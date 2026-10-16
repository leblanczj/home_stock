package com.homestock

import android.app.Application
import com.homestock.data.HomeStockDatabase
import com.homestock.data.InventoryRepository
import com.homestock.data.PhotoFiles

class HomeStockApp : Application() {
    lateinit var repository: InventoryRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = InventoryRepository(HomeStockDatabase.create(this), PhotoFiles(filesDir))
    }
}
