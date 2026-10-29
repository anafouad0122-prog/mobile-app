package com.fouad.cuttingnotebook.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.fouad.cuttingnotebook.data.sound.Cue
import com.fouad.cuttingnotebook.di.AppContainer
import com.fouad.cuttingnotebook.ui.history.HistoryScreen
import com.fouad.cuttingnotebook.ui.history.HistoryViewModel
import com.fouad.cuttingnotebook.ui.home.HomeScreen
import com.fouad.cuttingnotebook.ui.home.HomeViewModel
import com.fouad.cuttingnotebook.ui.lock.LockScreen
import com.fouad.cuttingnotebook.ui.operation.OperationFormScreen
import com.fouad.cuttingnotebook.ui.operation.OperationFormViewModel
import com.fouad.cuttingnotebook.ui.order.OrderDetailScreen
import com.fouad.cuttingnotebook.ui.order.OrderDetailViewModel
import com.fouad.cuttingnotebook.ui.order.OrderFormScreen
import com.fouad.cuttingnotebook.ui.order.OrderFormViewModel
import com.fouad.cuttingnotebook.ui.reports.ReportsScreen
import com.fouad.cuttingnotebook.ui.reports.ReportsViewModel
import com.fouad.cuttingnotebook.ui.settings.SettingsScreen
import com.fouad.cuttingnotebook.ui.settings.SettingsViewModel
import java.time.ZoneId
import kotlinx.coroutines.launch

private object Routes {
    const val HOME = "home"
    const val NEW_ORDER = "order/new"
    const val ORDER_DETAIL = "order/{orderId}"
    const val EDIT_ORDER = "order/{orderId}/edit"
    const val RECORD_CUT = "order/{orderId}/cut?itemId={itemId}"
    const val EDIT_OPERATION = "order/{orderId}/cut/{operationId}"
    const val HISTORY = "history"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    fun orderDetail(id: Long) = "order/$id"
    fun editOrder(id: Long) = "order/$id/edit"
    fun recordCut(orderId: Long, itemId: Long?) = "order/$orderId/cut?itemId=${itemId ?: -1L}"
    fun editOperation(orderId: Long, operationId: Long) = "order/$orderId/cut/$operationId"
}

/**
 * The navigation graph, behind the lock.
 *
 * Nothing below the lock is composed until the PIN has been accepted, so order data never
 * reaches the screen on a locked phone.
 */
@Composable
fun AppRoot(appViewModel: AppViewModel, container: AppContainer) {
    val lock by appViewModel.lock.collectAsState()
    val pinError by appViewModel.pinError.collectAsState()

    when (lock) {
        LockState.Loading -> Box(Modifier.fillMaxSize())

        LockState.NeedsSetup, LockState.Locked -> LockScreen(
            isSetup = lock == LockState.NeedsSetup,
            error = pinError,
            onSubmit = appViewModel::submitPin,
            onSetUp = appViewModel::setUpPin,
            onInputChanged = appViewModel::clearPinError,
        )

        LockState.Unlocked -> UnlockedApp(appViewModel, container)
    }
}

@Composable
private fun UnlockedApp(appViewModel: AppViewModel, container: AppContainer) {
    val navController = rememberNavController()
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val settings by appViewModel.settings.collectAsState()
    val zone = remember { ZoneId.systemDefault() }

    /** Shows a message and plays the matching cue once the work has actually finished. */
    fun report(message: String, success: Boolean) {
        appViewModel.play(if (success) Cue.SUCCESS else Cue.ERROR)
        scope.launch { snackbarHost.showSnackbar(message) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHost) }) { _ ->
        NavHost(navController = navController, startDestination = Routes.HOME) {

            composable(Routes.HOME) {
                val vm: HomeViewModel = viewModel(
                    factory = viewModelFactory { HomeViewModel(container.orders) },
                )
                HomeScreen(
                    viewModel = vm,
                    imageFileFor = container.images::fileFor,
                    onOpenOrder = { navController.navigate(Routes.orderDetail(it)) },
                    onNewOrder = { navController.navigate(Routes.NEW_ORDER) },
                    onOpenHistory = { navController.navigate(Routes.HISTORY) },
                    onOpenReports = { navController.navigate(Routes.REPORTS) },
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                )
            }

            composable(Routes.NEW_ORDER) {
                val vm: OrderFormViewModel = viewModel(
                    factory = viewModelFactory {
                        OrderFormViewModel(container.orders, container.images, orderId = null)
                    },
                )
                OrderFormScreen(
                    viewModel = vm,
                    imageFileFor = container.images::fileFor,
                    onBack = navController::popBackStack,
                    onSaved = { id ->
                        report(Ar.ORDER_SAVED, true)
                        navController.popBackStack()
                        navController.navigate(Routes.orderDetail(id))
                    },
                )
            }

            composable(
                route = Routes.EDIT_ORDER,
                arguments = listOf(navArgument("orderId") { type = NavType.LongType }),
            ) { entry ->
                val orderId = entry.arguments?.getLong("orderId") ?: return@composable
                val vm: OrderFormViewModel = viewModel(
                    factory = viewModelFactory {
                        OrderFormViewModel(container.orders, container.images, orderId)
                    },
                )
                OrderFormScreen(
                    viewModel = vm,
                    imageFileFor = container.images::fileFor,
                    onBack = navController::popBackStack,
                    onSaved = {
                        report(Ar.ORDER_UPDATED, true)
                        navController.popBackStack()
                    },
                )
            }

            composable(
                route = Routes.ORDER_DETAIL,
                arguments = listOf(navArgument("orderId") { type = NavType.LongType }),
            ) { entry ->
                val orderId = entry.arguments?.getLong("orderId") ?: return@composable
                val vm: OrderDetailViewModel = viewModel(
                    factory = viewModelFactory { OrderDetailViewModel(container.orders, orderId) },
                )
                OrderDetailScreen(
                    viewModel = vm,
                    imageFileFor = container.images::fileFor,
                    zone = zone,
                    checkPin = appViewModel::checkPin,
                    onBack = navController::popBackStack,
                    onEditOrder = { navController.navigate(Routes.editOrder(orderId)) },
                    onRecordCut = { itemId ->
                        navController.navigate(Routes.recordCut(orderId, itemId))
                    },
                    onEditOperation = { operationId ->
                        navController.navigate(Routes.editOperation(orderId, operationId))
                    },
                    onMessage = ::report,
                )
            }

            composable(
                route = Routes.RECORD_CUT,
                arguments = listOf(
                    navArgument("orderId") { type = NavType.LongType },
                    navArgument("itemId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                ),
            ) { entry ->
                val orderId = entry.arguments?.getLong("orderId") ?: return@composable
                val itemId = entry.arguments?.getLong("itemId")?.takeIf { it > 0 }
                CutScreen(
                    container = container,
                    appViewModel = appViewModel,
                    zone = zone,
                    orderId = orderId,
                    operationId = null,
                    initialItemId = itemId,
                    navigateBack = navController::popBackStack,
                    report = ::report,
                )
            }

            composable(
                route = Routes.EDIT_OPERATION,
                arguments = listOf(
                    navArgument("orderId") { type = NavType.LongType },
                    navArgument("operationId") { type = NavType.LongType },
                ),
            ) { entry ->
                val orderId = entry.arguments?.getLong("orderId") ?: return@composable
                val operationId = entry.arguments?.getLong("operationId") ?: return@composable
                CutScreen(
                    container = container,
                    appViewModel = appViewModel,
                    zone = zone,
                    orderId = orderId,
                    operationId = operationId,
                    initialItemId = null,
                    navigateBack = navController::popBackStack,
                    report = ::report,
                )
            }

            composable(Routes.HISTORY) {
                val vm: HistoryViewModel = viewModel(
                    factory = viewModelFactory { HistoryViewModel(container.orders, zone) },
                )
                HistoryScreen(
                    viewModel = vm,
                    imageFileFor = container.images::fileFor,
                    zone = zone,
                    onBack = navController::popBackStack,
                    onOpenOrder = { navController.navigate(Routes.orderDetail(it)) },
                )
            }

            composable(Routes.REPORTS) {
                val vm: ReportsViewModel = viewModel(
                    factory = viewModelFactory {
                        ReportsViewModel(container.database.operationDao(), zone)
                    },
                )
                ReportsScreen(viewModel = vm, onBack = navController::popBackStack)
            }

            composable(Routes.SETTINGS) {
                val vm: SettingsViewModel = viewModel(
                    factory = viewModelFactory {
                        SettingsViewModel(container.settings, container.backups)
                    },
                )
                SettingsScreen(
                    viewModel = vm,
                    settings = settings,
                    onBack = navController::popBackStack,
                    onMessage = ::report,
                )
            }
        }
    }
}

/**
 * The record/edit cut screen, wired to the completion cue.
 *
 * The success sound only plays after the repository has returned, and the overflow and
 * order-complete cues are layered on top of it (spec 11).
 */
@Composable
private fun CutScreen(
    container: AppContainer,
    appViewModel: AppViewModel,
    zone: ZoneId,
    orderId: Long,
    operationId: Long?,
    initialItemId: Long?,
    navigateBack: () -> Unit,
    report: (String, Boolean) -> Unit,
) {
    val vm: OperationFormViewModel = viewModel(
        factory = viewModelFactory {
            OperationFormViewModel(container.orders, orderId, operationId, initialItemId)
        },
    )
    val scope = rememberCoroutineScope()

    OperationFormScreen(
        viewModel = vm,
        zone = zone,
        onBack = navigateBack,
        onSaved = { overflowed ->
            report(
                if (operationId == null) Ar.OPERATION_SAVED else Ar.OPERATION_UPDATED,
                true,
            )
            if (overflowed) appViewModel.play(Cue.OVERFLOW)
            scope.launch {
                if (vm.orderIsComplete()) {
                    appViewModel.play(Cue.COMPLETE)
                    report(Ar.COMPLETED_TOAST, true)
                }
            }
            navigateBack()
        },
        onError = { message -> report(message, false) },
    )
}
