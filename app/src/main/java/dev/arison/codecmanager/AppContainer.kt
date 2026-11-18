package dev.arison.codecmanager

import android.content.Context
import dev.arison.codecmanager.bluetooth.A2dpCodecApi
import dev.arison.codecmanager.bluetooth.CodecEngine
import dev.arison.codecmanager.bluetooth.SystemCodecEngine
import dev.arison.codecmanager.data.EventLog
import dev.arison.codecmanager.data.SettingsRepository
import dev.arison.codecmanager.presence.CompanionAssociationManager
import dev.arison.codecmanager.service.CodecNotifier

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val associations = CompanionAssociationManager(appContext)
    val log = EventLog(appContext)
    val settings = SettingsRepository(appContext)
    val codecApi = A2dpCodecApi(appContext)
    val engine: CodecEngine = SystemCodecEngine(codecApi)
    val notifier = CodecNotifier(appContext)

    val coordinator = ConnectionCoordinator(
        context = appContext,
        engine = engine,
        settings = settings,
        associations = associations,
        log = log,
        notifier = notifier,
    )
}
