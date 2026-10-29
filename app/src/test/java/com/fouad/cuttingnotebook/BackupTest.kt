package com.fouad.cuttingnotebook

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.fouad.cuttingnotebook.data.backup.BackupError
import com.fouad.cuttingnotebook.data.backup.BackupManager
import com.fouad.cuttingnotebook.data.backup.BackupPayload
import com.fouad.cuttingnotebook.data.backup.BackupResult
import com.fouad.cuttingnotebook.data.db.CuttingDatabase
import com.fouad.cuttingnotebook.data.repo.ImageStore
import com.fouad.cuttingnotebook.data.repo.OrderRepository
import com.fouad.cuttingnotebook.data.repo.QuantityLine
import com.fouad.cuttingnotebook.data.settings.SettingsStore
import java.io.File
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Acceptance test 11: a backup round-trips, and a damaged file leaves the data alone. */
@RunWith(RobolectricTestRunner::class)
class BackupTest {

    private lateinit var context: Context
    private lateinit var db: CuttingDatabase
    private lateinit var repo: OrderRepository
    private lateinit var images: ImageStore
    private lateinit var backups: BackupManager
    private lateinit var workDir: File

    private val day = LocalDate.of(2026, 3, 10)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = TestFixtures.inMemoryDatabase()
        repo = TestFixtures.repository(db)
        images = ImageStore(context)
        backups = BackupManager(
            context = context,
            db = db,
            images = images,
            settingsStore = SettingsStore(context),
            appVersionCode = 1,
        )
        workDir = File(context.cacheDir, "backup-test").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        db.close()
        workDir.deleteRecursively()
    }

    private suspend fun seed(): Long {
        val orderId = repo.createOrder(
            TestFixtures.detailedOrder(
                lines = listOf(QuantityLine("أحمر", "4", 10), QuantityLine("أزرق", "6", 20)),
            ),
        ).expectSuccess()
        val red = repo.combinationsOnce(orderId).first { it.color == "أحمر" }
        repo.recordOperation(
            orderId,
            TestFixtures.directCut(red.itemId, 6, TestFixtures.millisAt(day)),
        ).expectSuccess()
        return orderId
    }

    @Test
    fun `a backup restores the orders, operations and totals it was taken from`() = runTest {
        seed()
        val file = File(workDir, "backup.zip")

        val exported = backups.export(Uri.fromFile(file))
        assertTrue("export failed: $exported", exported is BackupResult.Exported)
        assertTrue(file.length() > 0)

        // Wipe everything, as a fresh install would be.
        db.clearAllTables()
        assertTrue(db.orderDao().all().isEmpty())

        val restored = backups.restore(Uri.fromFile(file))
        assertTrue("restore failed: $restored", restored is BackupResult.Restored)

        val orders = db.orderDao().all()
        assertEquals(1, orders.size)
        val combinations = repo.combinationsOnce(orders.single().id)
        assertEquals(30, combinations.sumOf { it.required })
        assertEquals(6, combinations.sumOf { it.cut })
        assertEquals(24, combinations.sumOf { it.remaining })
        assertEquals(1, db.operationDao().all().size)
    }

    @Test
    fun `a model photo travels with the backup`() = runTest {
        val orderId = seed()
        val photoName = "photo-test.jpg"
        // Written straight into the photo directory, as importing a picked image would.
        val photoDir = File(context.filesDir, "model_images").apply { mkdirs() }
        File(photoDir, photoName).writeBytes(byteArrayOf(1, 2, 3, 4))
        val model = db.modelDao().all().single()
        db.modelDao().update(model.copy(imagePath = photoName))

        val file = File(workDir, "with-photo.zip")
        val exported = backups.export(Uri.fromFile(file)) as BackupResult.Exported
        assertEquals(1, exported.images)

        photoDir.deleteRecursively()
        db.clearAllTables()
        assertFalse(images.exists(photoName))

        backups.restore(Uri.fromFile(file))

        assertTrue(images.exists(photoName))
        assertEquals(photoName, db.modelDao().all().single().imagePath)
        assertEquals(orderId, db.orderDao().all().single().id)
    }

    @Test
    fun `a file that is not a backup is rejected and the data is left alone`() = runTest {
        seed()
        val before = db.operationDao().all()

        val junk = File(workDir, "junk.zip").apply { writeText("this is not a zip") }
        val result = backups.restore(Uri.fromFile(junk))

        assertEquals(BackupError.NOT_A_BACKUP, (result as BackupResult.Failed).reason)
        assertEquals(before.map { it.id }, db.operationDao().all().map { it.id })
        assertEquals(1, db.orderDao().all().size)
    }

    @Test
    fun `a backup whose rows do not line up is rejected before anything is written`() = runTest {
        seed()
        val payload = """
            {
              "format": "${BackupPayload.FORMAT_ID}",
              "formatVersion": 1,
              "schemaVersion": 1,
              "appVersionCode": 1,
              "createdAt": 0,
              "models": [],
              "orders": [
                {"id":1,"modelId":99,"isDetailed":false,"status":"OPEN","createdAt":0,"updatedAt":0}
              ],
              "orderItems": [],
              "operations": [],
              "settings": {"themeMode":"SYSTEM","accent":"GREEN","soundsEnabled":true,"buttonSoundsEnabled":false}
            }
        """.trimIndent()
        val file = File(workDir, "inconsistent.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupPayload.JSON_ENTRY))
            zip.write(payload.toByteArray())
            zip.closeEntry()
        }

        val result = backups.restore(Uri.fromFile(file))

        // The order points at a model that is not in the file.
        assertEquals(BackupError.INCONSISTENT, (result as BackupResult.Failed).reason)
        assertEquals(1, db.orderDao().all().size)
        assertEquals(1, db.operationDao().all().size)
    }

    @Test
    fun `a backup from a newer schema is refused rather than half-read`() = runTest {
        seed()
        val payload = """
            {
              "format": "${BackupPayload.FORMAT_ID}",
              "formatVersion": 1,
              "schemaVersion": ${CuttingDatabase.VERSION + 1},
              "appVersionCode": 99,
              "createdAt": 0,
              "models": [], "orders": [], "orderItems": [], "operations": [],
              "settings": {"themeMode":"SYSTEM","accent":"GREEN","soundsEnabled":true,"buttonSoundsEnabled":false}
            }
        """.trimIndent()
        val file = File(workDir, "future.zip")
        ZipOutputStream(file.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry(BackupPayload.JSON_ENTRY))
            zip.write(payload.toByteArray())
            zip.closeEntry()
        }

        val result = backups.restore(Uri.fromFile(file))

        assertEquals(BackupError.UNSUPPORTED_VERSION, (result as BackupResult.Failed).reason)
        assertEquals(1, db.orderDao().all().size)
    }

    @Test
    fun `the backup file carries no PIN material`() = runTest {
        val settings = SettingsStore(context)
        settings.saveVerifier(
            com.fouad.cuttingnotebook.data.settings.PinManager.createVerifier("4321"),
        )
        seed()

        val file = File(workDir, "no-pin.zip")
        backups.export(Uri.fromFile(file))

        val text = java.util.zip.ZipInputStream(file.inputStream()).use { zip ->
            generateSequence { zip.nextEntry }
                .firstOrNull { it.name == BackupPayload.JSON_ENTRY }
                ?.let { zip.readBytes().decodeToString() }
        }.orEmpty()

        assertTrue(text.isNotEmpty())
        val verifier = settings.storedVerifier()!!
        assertFalse(text.contains("pin", ignoreCase = true))
        assertFalse(text.contains(verifier.hash))
        assertFalse(text.contains(verifier.salt))
    }

    @Test
    fun `settings come back with the data`() = runTest {
        val settings = SettingsStore(context)
        settings.setAccent(com.fouad.cuttingnotebook.data.settings.AccentColor.CRIMSON)
        settings.setSoundsEnabled(false)
        seed()

        val file = File(workDir, "settings.zip")
        backups.export(Uri.fromFile(file))

        settings.setAccent(com.fouad.cuttingnotebook.data.settings.AccentColor.BLUE)
        settings.setSoundsEnabled(true)
        db.clearAllTables()

        backups.restore(Uri.fromFile(file))

        val after = settings.settings.first()
        assertEquals(com.fouad.cuttingnotebook.data.settings.AccentColor.CRIMSON, after.accent)
        assertFalse(after.soundsEnabled)
    }
}
