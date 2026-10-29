package com.fouad.cuttingnotebook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

/** Small helper so each screen's factory is one line rather than an anonymous class. */
inline fun <reified VM : ViewModel> viewModelFactory(crossinline build: () -> VM) =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = build() as T
    }
