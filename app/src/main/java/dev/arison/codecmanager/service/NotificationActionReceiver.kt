package dev.arison.codecmanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import dev.arison.codecmanager.CodecApp
import dev.arison.codecmanager.bluetooth.LdacQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as? CodecApp ?: return
        when (intent.action) {
            ACTION_DISABLE_AUTO_SWITCH -> {
                val pending = goAsync()
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        app.container.settings.setAutoSwitch(false)
                        app.container.log.i("Notify", "auto-switch disabled from notification")
                        CodecForegroundService.stop(context)
                        NotificationManagerCompat.from(context).cancel(CodecNotifier.ID_SERVICE)
                    } finally {
                        pending.finish()
                    }
                }
            }
            ACTION_APPLY_QUALITY -> {
                val mac = intent.getStringExtra(CodecForegroundService.EXTRA_MAC) ?: return
                val quality = LdacQuality.fromCode(
                    intent.getLongExtra(CodecForegroundService.EXTRA_QUALITY, LdacQuality.AUDIO_QUALITY.code),
                )
                val serviceIntent = Intent(context, CodecForegroundService::class.java)
                    .setAction(CodecForegroundService.ACTION_APPLY)
                    .putExtra(CodecForegroundService.EXTRA_MAC, mac)
                    .putExtra(CodecForegroundService.EXTRA_QUALITY, quality.code)
                runCatching { context.startForegroundService(serviceIntent) }
            }
        }
    }

    companion object {
        const val ACTION_DISABLE_AUTO_SWITCH = "dev.arison.codecmanager.action.DISABLE_AUTO_SWITCH"
        const val ACTION_APPLY_QUALITY = "dev.arison.codecmanager.action.APPLY_QUALITY"
    }
}
