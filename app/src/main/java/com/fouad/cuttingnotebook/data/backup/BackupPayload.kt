package com.fouad.cuttingnotebook.data.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The on-disk backup format (spec 15).
 *
 * [formatVersion] describes this envelope and [schemaVersion] the database it was taken from,
 * so a future release can tell "written by an older app" from "written by a newer one" and
 * refuse or migrate accordingly.
 *
 * The PIN verifier is deliberately absent: a backup must not carry anything that could be
 * used to get past the lock screen.
 */
@Serializable
data class BackupPayload(
    @SerialName("format") val format: String = FORMAT_ID,
    @SerialName("formatVersion") val formatVersion: Int = FORMAT_VERSION,
    @SerialName("schemaVersion") val schemaVersion: Int,
    @SerialName("appVersionCode") val appVersionCode: Int,
    @SerialName("createdAt") val createdAt: Long,
    @SerialName("models") val models: List<BackupModel>,
    @SerialName("orders") val orders: List<BackupOrder>,
    @SerialName("orderItems") val orderItems: List<BackupOrderItem>,
    @SerialName("operations") val operations: List<BackupOperation>,
    @SerialName("settings") val settings: BackupSettings,
) {
    companion object {
        const val FORMAT_ID = "cutting-notebook-backup"
        const val FORMAT_VERSION = 1
        const val JSON_ENTRY = "backup.json"
        const val IMAGE_DIR = "images/"
    }
}

@Serializable
data class BackupModel(
    val id: Long,
    val name: String,
    val code: String? = null,
    val imageName: String? = null,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupOrder(
    val id: Long,
    val modelId: Long,
    val isDetailed: Boolean,
    val status: String,
    val notes: String? = null,
    val createdAt: Long,
    val closedAt: Long? = null,
    val updatedAt: Long,
)

@Serializable
data class BackupOrderItem(
    val id: Long,
    val orderId: Long,
    val color: String,
    val size: String,
    val requiredQty: Int,
)

@Serializable
data class BackupOperation(
    val id: Long,
    val orderId: Long,
    val orderItemId: Long,
    val pieces: Int,
    val entryMethod: String,
    val layers: Int? = null,
    val piecesPerLayer: Int? = null,
    val performedAt: Long,
    val notes: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class BackupSettings(
    val themeMode: String,
    val accent: String,
    val soundsEnabled: Boolean,
    val buttonSoundsEnabled: Boolean,
)
