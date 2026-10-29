package com.fouad.cuttingnotebook.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.fouad.cuttingnotebook.data.db.CutOperationEntity
import com.fouad.cuttingnotebook.data.db.CutOrderEntity
import com.fouad.cuttingnotebook.data.db.CuttingDatabase
import com.fouad.cuttingnotebook.data.db.EntryMethod
import com.fouad.cuttingnotebook.data.db.ModelEntity
import com.fouad.cuttingnotebook.data.db.OrderItemEntity
import com.fouad.cuttingnotebook.data.db.OrderStatus
import com.fouad.cuttingnotebook.data.repo.ImageStore
import com.fouad.cuttingnotebook.data.settings.AccentColor
import com.fouad.cuttingnotebook.data.settings.AppSettings
import com.fouad.cuttingnotebook.data.settings.SettingsStore
import com.fouad.cuttingnotebook.data.settings.ThemeMode
import java.io.BufferedInputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

sealed interface BackupResult {
    data class Exported(val models: Int, val orders: Int, val operations: Int, val images: Int) :
        BackupResult

    data class Restored(val models: Int, val orders: Int, val operations: Int, val images: Int) :
        BackupResult

    data class Failed(val reason: BackupError) : BackupResult
}

enum class BackupError {
    /** The file is not a zip, or has no backup.json inside. */
    NOT_A_BACKUP,

    /** backup.json is present but unreadable or not this app's format. */
    CORRUPT,

    /** Written by a newer app than this one. */
    UNSUPPORTED_VERSION,

    /** Rows point at models, orders or items that are not in the file. */
    INCONSISTENT,

    /** Ran out of space while writing. */
    OUT_OF_SPACE,

    /** Anything else from the file system. */
    IO,
}

/**
 * Writes and reads the app's backup file (spec 15).
 *
 * A restore validates the whole payload before touching anything, then replaces the data in a
 * single database transaction with the photos staged alongside. A failure at any point leaves
 * the notebook exactly as it was.
 */
class BackupManager(
    context: Context,
    private val db: CuttingDatabase,
    private val images: ImageStore,
    private val settingsStore: SettingsStore,
    private val appVersionCode: Int,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val appContext = context.applicationContext
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(destination: Uri): BackupResult = withContext(Dispatchers.IO) {
        try {
            val models = db.modelDao().all()
            val orders = db.orderDao().all()
            val items = db.orderItemDao().all()
            val operations = db.operationDao().all()
            val settings = settingsStore.settings.first()

            val payload = BackupPayload(
                schemaVersion = CuttingDatabase.VERSION,
                appVersionCode = appVersionCode,
                createdAt = now(),
                models = models.map { it.toBackup() },
                orders = orders.map { it.toBackup() },
                orderItems = items.map { it.toBackup() },
                operations = operations.map { it.toBackup() },
                settings = settings.toBackup(),
            )

            val referenced = models.mapNotNull { it.imagePath }.toSet()
            var imageCount = 0

            val stream = appContext.contentResolver.openOutputStream(destination, "wt")
                ?: return@withContext BackupResult.Failed(BackupError.IO)

            ZipOutputStream(stream.buffered()).use { zip ->
                zip.putNextEntry(ZipEntry(BackupPayload.JSON_ENTRY))
                zip.write(json.encodeToString(BackupPayload.serializer(), payload).toByteArray())
                zip.closeEntry()

                for (file in images.listAll()) {
                    if (file.name !in referenced) continue
                    zip.putNextEntry(ZipEntry(BackupPayload.IMAGE_DIR + file.name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    imageCount++
                }
            }

            BackupResult.Exported(
                models = models.size,
                orders = orders.size,
                operations = operations.size,
                images = imageCount,
            )
        } catch (e: IOException) {
            BackupResult.Failed(e.toBackupError())
        }
    }

    suspend fun restore(source: Uri): BackupResult = withContext(Dispatchers.IO) {
        val parsed = try {
            readArchive(source)
        } catch (e: IOException) {
            return@withContext BackupResult.Failed(e.toBackupError())
        }
        val archive = when (parsed) {
            is ParsedArchive.Failure -> return@withContext BackupResult.Failed(parsed.error)
            is ParsedArchive.Success -> parsed
        }

        val payload = archive.payload
        validate(payload)?.let { return@withContext BackupResult.Failed(it) }

        try {
            images.beginStaging()
            for ((name, bytes) in archive.images) {
                images.stage(name, bytes)
            }

            db.withTransaction {
                // order_items and cut_operations cascade from cut_orders, but they are cleared
                // explicitly so the wipe does not depend on foreign-key enforcement being on.
                db.clearAllTables()
                val modelDao = db.modelDao()
                val orderDao = db.orderDao()
                val itemDao = db.orderItemDao()
                val operationDao = db.operationDao()

                payload.models.forEach { modelDao.insert(it.toEntity()) }
                payload.orders.forEach { orderDao.insert(it.toEntity()) }
                payload.orderItems.forEach { itemDao.insert(it.toEntity()) }
                payload.operations.forEach { operationDao.insert(it.toEntity()) }
            }

            if (!images.commitStaging()) {
                return@withContext BackupResult.Failed(BackupError.IO)
            }

            settingsStore.setThemeMode(payload.settings.themeMode.toThemeMode())
            settingsStore.setAccent(payload.settings.accent.toAccent())
            settingsStore.setSoundsEnabled(payload.settings.soundsEnabled)
            settingsStore.setButtonSoundsEnabled(payload.settings.buttonSoundsEnabled)

            BackupResult.Restored(
                models = payload.models.size,
                orders = payload.orders.size,
                operations = payload.operations.size,
                images = archive.images.size,
            )
        } catch (e: Exception) {
            images.discardStaging()
            BackupResult.Failed(if (e is IOException) e.toBackupError() else BackupError.CORRUPT)
        }
    }

    // ---- reading ------------------------------------------------------------------------

    private sealed interface ParsedArchive {
        data class Success(
            val payload: BackupPayload,
            val images: Map<String, ByteArray>,
        ) : ParsedArchive

        data class Failure(val error: BackupError) : ParsedArchive
    }

    private fun readArchive(source: Uri): ParsedArchive {
        val stream = appContext.contentResolver.openInputStream(source)
            ?: return ParsedArchive.Failure(BackupError.IO)

        var payload: BackupPayload? = null
        val imageBytes = mutableMapOf<String, ByteArray>()

        ZipInputStream(BufferedInputStream(stream)).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                when {
                    name == BackupPayload.JSON_ENTRY -> {
                        val text = zip.readBytes().decodeToString()
                        payload = runCatching {
                            json.decodeFromString(BackupPayload.serializer(), text)
                        }.getOrNull() ?: return ParsedArchive.Failure(BackupError.CORRUPT)
                    }

                    name.startsWith(BackupPayload.IMAGE_DIR) && !entry.isDirectory -> {
                        val fileName = name.removePrefix(BackupPayload.IMAGE_DIR)
                        // A crafted archive must not be able to write outside the photo folder.
                        if (fileName.isNotEmpty() && !fileName.contains('/') && fileName != "..") {
                            imageBytes[fileName] = zip.readBytes()
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val found = payload ?: return ParsedArchive.Failure(BackupError.NOT_A_BACKUP)
        if (found.format != BackupPayload.FORMAT_ID) {
            return ParsedArchive.Failure(BackupError.NOT_A_BACKUP)
        }
        if (found.formatVersion > BackupPayload.FORMAT_VERSION ||
            found.schemaVersion > CuttingDatabase.VERSION
        ) {
            return ParsedArchive.Failure(BackupError.UNSUPPORTED_VERSION)
        }
        return ParsedArchive.Success(found, imageBytes)
    }

    /** Rejects a file whose rows do not line up, before any of it is written. */
    private fun validate(payload: BackupPayload): BackupError? {
        val modelIds = payload.models.map { it.id }.toSet()
        if (modelIds.size != payload.models.size) return BackupError.INCONSISTENT

        val orderIds = payload.orders.map { it.id }.toSet()
        if (orderIds.size != payload.orders.size) return BackupError.INCONSISTENT
        if (payload.orders.any { it.modelId !in modelIds }) return BackupError.INCONSISTENT
        if (payload.orders.any { runCatching { OrderStatus.valueOf(it.status) }.isFailure }) {
            return BackupError.CORRUPT
        }

        val itemIds = payload.orderItems.map { it.id }.toSet()
        if (itemIds.size != payload.orderItems.size) return BackupError.INCONSISTENT
        if (payload.orderItems.any { it.orderId !in orderIds }) return BackupError.INCONSISTENT
        if (payload.orderItems.any { it.requiredQty < 0 }) return BackupError.INCONSISTENT

        val itemsById = payload.orderItems.associateBy { it.id }
        for (op in payload.operations) {
            if (op.orderId !in orderIds) return BackupError.INCONSISTENT
            val item = itemsById[op.orderItemId] ?: return BackupError.INCONSISTENT
            if (item.orderId != op.orderId) return BackupError.INCONSISTENT
            if (op.pieces <= 0) return BackupError.INCONSISTENT
            if (runCatching { EntryMethod.valueOf(op.entryMethod) }.isFailure) {
                return BackupError.CORRUPT
            }
        }
        if (payload.operations.map { it.id }.toSet().size != payload.operations.size) {
            return BackupError.INCONSISTENT
        }
        return null
    }
}

// ---- mapping ------------------------------------------------------------------------------

private fun ModelEntity.toBackup() = BackupModel(
    id = id,
    name = name,
    code = code,
    imageName = imagePath,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun BackupModel.toEntity() = ModelEntity(
    id = id,
    name = name,
    code = code,
    imagePath = imageName,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun CutOrderEntity.toBackup() = BackupOrder(
    id = id,
    modelId = modelId,
    isDetailed = isDetailed,
    status = status.name,
    notes = notes,
    createdAt = createdAt,
    closedAt = closedAt,
    updatedAt = updatedAt,
)

private fun BackupOrder.toEntity() = CutOrderEntity(
    id = id,
    modelId = modelId,
    isDetailed = isDetailed,
    status = OrderStatus.valueOf(status),
    notes = notes,
    createdAt = createdAt,
    closedAt = closedAt,
    updatedAt = updatedAt,
)

private fun OrderItemEntity.toBackup() = BackupOrderItem(
    id = id,
    orderId = orderId,
    color = color,
    size = size,
    requiredQty = requiredQty,
)

private fun BackupOrderItem.toEntity() = OrderItemEntity(
    id = id,
    orderId = orderId,
    color = color,
    size = size,
    requiredQty = requiredQty,
)

private fun CutOperationEntity.toBackup() = BackupOperation(
    id = id,
    orderId = orderId,
    orderItemId = orderItemId,
    pieces = pieces,
    entryMethod = entryMethod.name,
    layers = layers,
    piecesPerLayer = piecesPerLayer,
    performedAt = performedAt,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun BackupOperation.toEntity() = CutOperationEntity(
    id = id,
    orderId = orderId,
    orderItemId = orderItemId,
    pieces = pieces,
    entryMethod = EntryMethod.valueOf(entryMethod),
    layers = layers,
    piecesPerLayer = piecesPerLayer,
    performedAt = performedAt,
    notes = notes,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun AppSettings.toBackup() = BackupSettings(
    themeMode = themeMode.name,
    accent = accent.name,
    soundsEnabled = soundsEnabled,
    buttonSoundsEnabled = buttonSoundsEnabled,
)

private fun String.toThemeMode() =
    runCatching { ThemeMode.valueOf(this) }.getOrDefault(ThemeMode.SYSTEM)

private fun String.toAccent() =
    runCatching { AccentColor.valueOf(this) }.getOrDefault(AccentColor.GREEN)

private fun IOException.toBackupError(): BackupError {
    val text = (message ?: "").lowercase()
    return if ("space" in text || "enospc" in text) BackupError.OUT_OF_SPACE else BackupError.IO
}
