package com.fouad.cuttingnotebook

import android.app.Application
import com.fouad.cuttingnotebook.di.AppContainer

class CuttingNotebookApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
