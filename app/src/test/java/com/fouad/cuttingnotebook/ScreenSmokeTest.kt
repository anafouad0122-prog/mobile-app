package com.fouad.cuttingnotebook

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.unit.LayoutDirection
import com.fouad.cuttingnotebook.data.db.CuttingDatabase
import com.fouad.cuttingnotebook.data.repo.ImageStore
import com.fouad.cuttingnotebook.data.repo.OrderRepository
import com.fouad.cuttingnotebook.data.repo.QuantityLine
import com.fouad.cuttingnotebook.data.settings.AccentColor
import com.fouad.cuttingnotebook.data.settings.ThemeMode
import com.fouad.cuttingnotebook.ui.Ar
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
import com.fouad.cuttingnotebook.ui.theme.CuttingNotebookTheme
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Renders each screen against a real database.
 *
 * These do not assert on layout; they exist because a Compose screen can compile and still
 * throw the first time it is composed, and this project is built without a device to hand.
 */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "ar-rSA-ldrtl-w411dp-h891dp")
class ScreenSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    private lateinit var db: CuttingDatabase
    private lateinit var repo: OrderRepository
    private lateinit var images: ImageStore
    private var orderId: Long = 0
    private var itemId: Long = 0
    private var operationId: Long = 0

    private val zone: ZoneId = ZoneId.of("Africa/Cairo")

    @Before
    fun setUp() = runBlocking {
        db = TestFixtures.inMemoryDatabase()
        repo = TestFixtures.repository(db)
        images = ImageStore(androidx.test.core.app.ApplicationProvider.getApplicationContext())

        orderId = repo.createOrder(
            TestFixtures.detailedOrder(
                lines = listOf(QuantityLine("أحمر", "4", 10), QuantityLine("أزرق", "6", 20)),
            ),
        ).expectSuccess()
        itemId = repo.combinationsOnce(orderId).first().itemId
        repo.recordOperation(
            orderId,
            TestFixtures.directCut(itemId, 4, TestFixtures.millisAt(LocalDate.of(2026, 3, 10), zone)),
        ).expectSuccess()
        operationId = repo.observeOperations(orderId).first().single().id
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun render(content: @Composable () -> Unit) {
        compose.setContent {
            CuttingNotebookTheme(themeMode = ThemeMode.LIGHT, accent = AccentColor.GREEN) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    content()
                }
            }
        }
        compose.waitForIdle()
    }

    @Test
    fun `lock screen renders`() {
        render {
            LockScreen(
                isSetup = false,
                error = null,
                onSubmit = {},
                onSetUp = { _, _ -> },
                onInputChanged = {},
            )
        }
        compose.onNodeWithText(Ar.LOCK_TITLE).assertIsDisplayed()
    }

    @Test
    fun `first-run lock screen renders in setup mode`() {
        render {
            LockScreen(
                isSetup = true,
                error = null,
                onSubmit = {},
                onSetUp = { _, _ -> },
                onInputChanged = {},
            )
        }
        compose.onNodeWithText(Ar.LOCK_SETUP_TITLE).assertIsDisplayed()
    }

    @Test
    fun `home screen renders the open order`() {
        render {
            HomeScreen(
                viewModel = HomeViewModel(repo),
                imageFileFor = images::fileFor,
                onOpenOrder = {},
                onNewOrder = {},
                onOpenHistory = {},
                onOpenReports = {},
                onOpenSettings = {},
            )
        }
        compose.onNodeWithText("M-101").assertIsDisplayed()
    }

    @Test
    fun `new order form renders`() {
        render {
            OrderFormScreen(
                viewModel = OrderFormViewModel(repo, images, orderId = null),
                imageFileFor = images::fileFor,
                onBack = {},
                onSaved = {},
            )
        }
        compose.onNodeWithText(Ar.ORDER_NEW_TITLE).assertIsDisplayed()
    }

    @Test
    fun `edit order form loads the existing quantities`() {
        render {
            OrderFormScreen(
                viewModel = OrderFormViewModel(repo, images, orderId = orderId),
                imageFileFor = images::fileFor,
                onBack = {},
                onSaved = {},
            )
        }
        compose.onNodeWithText(Ar.ORDER_EDIT_TITLE).assertIsDisplayed()
    }

    @Test
    fun `order detail renders totals and the operations log`() {
        render {
            OrderDetailScreen(
                viewModel = OrderDetailViewModel(repo, orderId),
                imageFileFor = images::fileFor,
                zone = zone,
                checkPin = { true },
                onBack = {},
                onEditOrder = {},
                onRecordCut = {},
                onEditOperation = {},
                onMessage = { _, _ -> },
            )
        }
        compose.onNodeWithText(Ar.OPERATIONS_LOG).assertIsDisplayed()
    }

    @Test
    fun `cut screen renders for a new operation`() {
        render {
            OperationFormScreen(
                viewModel = OperationFormViewModel(repo, orderId, null, itemId),
                zone = zone,
                onBack = {},
                onSaved = {},
                onError = {},
            )
        }
        compose.onNodeWithText(Ar.OPERATION_NEW_TITLE).assertIsDisplayed()
    }

    @Test
    fun `cut screen renders when editing an operation`() {
        render {
            OperationFormScreen(
                viewModel = OperationFormViewModel(repo, orderId, operationId, null),
                zone = zone,
                onBack = {},
                onSaved = {},
                onError = {},
            )
        }
        compose.onNodeWithText(Ar.OPERATION_EDIT_TITLE).assertIsDisplayed()
    }

    @Test
    fun `history screen renders`() {
        render {
            HistoryScreen(
                viewModel = HistoryViewModel(repo, zone),
                imageFileFor = images::fileFor,
                zone = zone,
                onBack = {},
                onOpenOrder = {},
            )
        }
        compose.onNodeWithText(Ar.HISTORY_TITLE).assertIsDisplayed()
    }

    @Test
    fun `reports screen renders`() {
        render {
            ReportsScreen(
                viewModel = ReportsViewModel(db.operationDao(), zone) {
                    LocalDate.of(2026, 3, 10)
                },
                onBack = {},
            )
        }
        compose.onNodeWithText(Ar.TOTAL_PIECES).assertIsDisplayed()
    }

    @Test
    fun `dark theme renders the same screen`() {
        compose.setContent {
            CuttingNotebookTheme(themeMode = ThemeMode.DARK, accent = AccentColor.CRIMSON) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    HomeScreen(
                        viewModel = HomeViewModel(repo),
                        imageFileFor = images::fileFor,
                        onOpenOrder = {},
                        onNewOrder = {},
                        onOpenHistory = {},
                        onOpenReports = {},
                        onOpenSettings = {},
                    )
                }
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("M-101").assertIsDisplayed()
    }
}
