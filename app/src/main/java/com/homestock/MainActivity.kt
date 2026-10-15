package com.homestock

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.homestock.ui.AppNav
import com.homestock.ui.HomeStockTheme
import com.homestock.ui.InventoryViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeStockTheme {
                val viewModel: InventoryViewModel = viewModel()
                AppNav(viewModel)
            }
        }
    }
}
