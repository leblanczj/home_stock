package com.homestock.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.runtime.collectAsState

private object Routes {
    const val Home = "home"
    const val Expiry = "expiry"
    const val Places = "places"
    const val Inventory = "inventory"
    const val Product = "product/{id}"
    const val Purchase = "purchase?productId={productId}&entryId={entryId}"
    const val Count = "count?productId={productId}&entryId={entryId}"
    const val Consume = "consume?productId={productId}&entryId={entryId}"
    const val Discard = "discard?productId={productId}&entryId={entryId}"
    const val Stay = "stay?entryId={entryId}"

    fun product(id: Long) = "product/$id"
    fun purchase(productId: Long = -1L, entryId: Long = -1L) = "purchase?productId=$productId&entryId=$entryId"
    fun count(productId: Long = -1L, entryId: Long = -1L) = "count?productId=$productId&entryId=$entryId"
    fun consume(productId: Long = -1L, entryId: Long = -1L) = "consume?productId=$productId&entryId=$entryId"
    fun discard(productId: Long = -1L, entryId: Long = -1L) = "discard?productId=$productId&entryId=$entryId"
    fun stay(entryId: Long = -1L) = "stay?entryId=$entryId"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNav(viewModel: InventoryViewModel) {
    val nav = rememberNavController()
    val state by viewModel.state.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbar.showSnackbar(it) }
    }
    val current by nav.currentBackStackEntryAsState()
    val route = current?.destination?.route
    val topLevel = route == Routes.Home || route == Routes.Expiry || route == Routes.Places
    var settings by remember { mutableStateOf(false) }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            if (topLevel) {
                HomeTopBar(
                    residences = state.input.residences,
                    selectedId = state.residenceId,
                    onSelect = viewModel::selectResidence,
                    onSettings = { settings = true },
                )
            } else {
                TopAppBar(
                    title = { Text("家庭日用品库存") },
                    navigationIcon = { TextButton(onClick = { nav.popBackStack() }) { Text("返回") } },
                )
            }
        },
        bottomBar = {
            if (topLevel) {
                NavigationBar {
                    NavigationBarItem(
                        selected = route == Routes.Home,
                        onClick = { nav.openTop(Routes.Home) },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "首页") },
                        label = { Text("首页") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.Expiry,
                        onClick = { nav.openTop(Routes.Expiry) },
                        icon = { Icon(Icons.Filled.DateRange, contentDescription = "临期") },
                        label = { Text("临期") },
                    )
                    NavigationBarItem(
                        selected = route == Routes.Places,
                        onClick = { nav.openTop(Routes.Places) },
                        icon = { Icon(Icons.Filled.Place, contentDescription = "住所") },
                        label = { Text("住所") },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.Home, modifier = Modifier.padding(padding)) {
            composable(Routes.Home) {
                HomeScreen(
                    state = state,
                    onOpenProduct = { nav.navigate(Routes.product(it)) },
                    onInventory = { nav.navigate(Routes.Inventory) },
                    onPurchase = { nav.navigate(Routes.purchase()) },
                    onCount = { nav.navigate(Routes.count()) },
                    onConsume = { nav.navigate(Routes.consume()) },
                    onDiscard = { nav.navigate(Routes.discard()) },
                    onPlaces = { nav.openTop(Routes.Places) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                )
            }
            composable(Routes.Expiry) {
                ExpiryScreen(state, { nav.navigate(Routes.product(it)) }, androidx.compose.foundation.layout.PaddingValues())
            }
            composable(Routes.Places) {
                PlaceScreen(
                    state = state,
                    viewModel = viewModel,
                    onEditStay = { nav.navigate(Routes.stay(it)) },
                    onAddStay = { nav.navigate(Routes.stay()) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                )
            }
            composable(Routes.Inventory) {
                InventoryScreen(state, { nav.navigate(Routes.product(it)) }, androidx.compose.foundation.layout.PaddingValues())
            }
            composable(Routes.Product, listOf(navArgument("id") { type = NavType.LongType })) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                ProductScreen(
                    state = state,
                    productId = id,
                    viewModel = viewModel,
                    onPurchase = { nav.navigate(Routes.purchase(productId = id)) },
                    onCount = { nav.navigate(Routes.count(productId = id)) },
                    onConsume = { nav.navigate(Routes.consume(productId = id)) },
                    onDiscard = { nav.navigate(Routes.discard(productId = id)) },
                    onEditPurchase = { nav.navigate(Routes.purchase(productId = id, entryId = it)) },
                    onEditCount = { nav.navigate(Routes.count(productId = id, entryId = it)) },
                    onEditConsumption = { nav.navigate(Routes.consume(productId = id, entryId = it)) },
                    onEditDiscard = { nav.navigate(Routes.discard(productId = id, entryId = it)) },
                    onDeleted = { nav.popBackStack() },
                )
            }
            composable(
                Routes.Purchase,
                listOf(
                    navArgument("productId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("entryId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                PurchaseForm(
                    state = state,
                    productId = entry.optionalId("productId"),
                    entryId = entry.optionalId("entryId"),
                    viewModel = viewModel,
                    onDone = { nav.popBackStack() },
                )
            }
            composable(
                Routes.Count,
                listOf(
                    navArgument("productId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("entryId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                CountForm(
                    state, entry.optionalId("productId"), entry.optionalId("entryId"), viewModel,
                    onPurchase = { nav.navigate(Routes.purchase()) },
                    onDone = { nav.popBackStack() },
                )
            }
            composable(
                Routes.Consume,
                listOf(
                    navArgument("productId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("entryId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                ConsumeForm(
                    state, entry.optionalId("productId"), entry.optionalId("entryId"), viewModel,
                    onPurchase = { nav.navigate(Routes.purchase()) },
                    onDone = { nav.popBackStack() },
                )
            }
            composable(
                Routes.Discard,
                listOf(
                    navArgument("productId") { type = NavType.LongType; defaultValue = -1L },
                    navArgument("entryId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                DiscardForm(
                    state, entry.optionalId("productId"), entry.optionalId("entryId"), viewModel,
                    onPurchase = { nav.navigate(Routes.purchase()) },
                    onDone = { nav.popBackStack() },
                )
            }
            composable(
                Routes.Stay,
                listOf(navArgument("entryId") { type = NavType.LongType; defaultValue = -1L }),
            ) { entry ->
                StayForm(
                    state = state,
                    stayId = entry.arguments?.getLong("entryId") ?: -1L,
                    viewModel = viewModel,
                    onDone = { nav.popBackStack() },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(),
                )
            }
        }
    }
    if (settings) {
        LookbackDialog(state.input.lookback, { viewModel.setLookback(it) }) { settings = false }
    }
}

private fun androidx.navigation.NavHostController.openTop(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun androidx.navigation.NavBackStackEntry.optionalId(name: String): Long? =
    arguments?.getLong(name)?.takeIf { it >= 0 }
