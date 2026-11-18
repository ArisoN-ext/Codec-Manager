package dev.arison.codecmanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.arison.codecmanager.CodecApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        val app = context.applicationContext as? CodecApp ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val settings = app.container.settings.settings.first()
                if (settings.autoSwitch && settings.startOnBoot) {
                    CodecForegroundService.start(context)
                    app.container.log.i("Boot", "auto-switch service started on boot")
                }
            } finally {
                pending.finish()
            }
        }
    }
}
