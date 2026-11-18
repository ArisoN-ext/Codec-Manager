package dev.arison.codecmanager

import android.app.Application

class CodecApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notifier.ensureChannels()
    }
}
