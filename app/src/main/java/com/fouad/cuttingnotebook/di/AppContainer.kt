package com.fouad.cuttingnotebook.di

import android.content.Context
import com.fouad.cuttingnotebook.BuildConfig
import com.fouad.cuttingnotebook.data.backup.BackupManager
import com.fouad.cuttingnotebook.data.db.CuttingDatabase
import com.fouad.cuttingnotebook.data.repo.ImageStore
import com.fouad.cuttingnotebook.data.repo.OrderRepository
import com.fouad.cuttingnotebook.data.settings.SettingsStore
import com.fouad.cuttingnotebook.data.sound.SoundPlayer

/**
 * Hand-rolled dependency graph.
 *
 * The app has one database, one settings store and one sound pool; a DI framework would add
 * build machinery without removing any wiring worth removing.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val database: CuttingDatabase by lazy { CuttingDatabase.build(appContext) }
    val settings: SettingsStore by lazy { SettingsStore(appContext) }
    val images: ImageStore by lazy { ImageStore(appContext) }
    val orders: OrderRepository by lazy { OrderRepository(database) }
    val sounds: SoundPlayer by lazy { SoundPlayer(appContext) }

    val backups: BackupManager by lazy {
        BackupManager(
            context = appContext,
            db = database,
            images = images,
            settingsStore = settings,
            appVersionCode = BuildConfig.VERSION_CODE,
        )
    }
}
