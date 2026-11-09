package com.safestep.mobile

import android.app.Application
import com.safestep.mobile.core.AppContainer

class SafeStepApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.create(this)
    }
}
