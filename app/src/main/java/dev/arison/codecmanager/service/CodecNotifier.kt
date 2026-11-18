package dev.arison.codecmanager.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dev.arison.codecmanager.MainActivity
import dev.arison.codecmanager.R
import dev.arison.codecmanager.bluetooth.ApplyResult
import dev.arison.codecmanager.bluetooth.FailureReason
import dev.arison.codecmanager.bluetooth.LdacQuality

class CodecNotifier(private val context: Context) {

    fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val status = NotificationChannel(
            CHANNEL_STATUS,
            context.getString(R.string.notification_channel_status),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.notification_channel_status_desc)
            setShowBadge(false)
        }
        val service = NotificationChannel(
            CHANNEL_SERVICE,
            context.getString(R.string.notification_channel_status),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            setShowBadge(false)
            setSound(null, null)
        }
        manager.createNotificationChannels(listOf(status, service))
    }

    fun serviceNotification(deviceName: String? = null): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val disable = PendingIntent.getBroadcast(
            context,
            1,
            Intent(context, NotificationActionReceiver::class.java)
                .setAction(NotificationActionReceiver.ACTION_DISABLE_AUTO_SWITCH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = deviceName?.let { context.getString(R.string.notification_service_device, it) }
            ?: context.getString(R.string.notification_service_text)
        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setContentText(text)
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .addAction(0, context.getString(R.string.notification_action_disable), disable)
            .build()
    }

    fun notifyResult(mac: String, deviceName: String, quality: LdacQuality?, result: ApplyResult) {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(deviceName)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)

        when (result) {
            is ApplyResult.Applied -> {
                val qualityText = quality?.bitrateLabel ?: ""
                builder.setContentText(
                    context.getString(R.string.notification_applied, "LDAC", qualityText),
                )
                if (result.unverifiedQuality) {
                    builder.setSubText(context.getString(R.string.home_apply_unverified))
                }
            }

            is ApplyResult.AlreadyApplied -> builder.setContentText(
                context.getString(R.string.home_apply_noop),
            )

            is ApplyResult.Rejected -> builder.setContentText(
                context.getString(R.string.error_rejected),
            )

            is ApplyResult.Failure -> builder.setContentText(
                context.getString(reasonText(result.reason)),
            )
        }

        val qualityIntent = qualityAction(mac, LdacQuality.AUDIO_QUALITY, 10)
        val balancedIntent = qualityAction(mac, LdacQuality.BALANCED, 11)
        if (result is ApplyResult.Rejected || result is ApplyResult.Failure) {
            builder.addAction(
                0,
                context.getString(R.string.notification_action_quality),
                qualityIntent,
            )
            builder.addAction(
                0,
                context.getString(R.string.notification_action_balanced),
                balancedIntent,
            )
        }

        runCatching {
            NotificationManagerCompat.from(context).notify(ID_RESULT, builder.build())
        }
    }

    private fun qualityAction(mac: String, quality: LdacQuality, requestCode: Int): PendingIntent {
        val intent = Intent(context, CodecForegroundService::class.java)
            .setAction(CodecForegroundService.ACTION_APPLY)
            .putExtra(CodecForegroundService.EXTRA_MAC, mac)
            .putExtra(CodecForegroundService.EXTRA_QUALITY, quality.code)
        return PendingIntent.getForegroundService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    fun reasonText(reason: FailureReason): Int = when (reason) {
        FailureReason.NOT_ASSOCIATED -> R.string.error_not_associated
        FailureReason.PERMISSION_DENIED -> R.string.error_permission
        FailureReason.HIDDEN_API_UNAVAILABLE -> R.string.error_hidden_api
        FailureReason.ADAPTER_OFF -> R.string.error_adapter_off
        FailureReason.NOT_CONNECTED -> R.string.error_not_connected
        FailureReason.CODEC_NOT_SUPPORTED -> R.string.error_hidden_api
        FailureReason.REJECTED -> R.string.error_rejected
        FailureReason.TIMEOUT -> R.string.error_timeout
        FailureReason.UNKNOWN -> R.string.error_unknown
    }

    companion object {
        const val CHANNEL_STATUS = "codec_status"
        const val CHANNEL_SERVICE = "codec_service"
        const val ID_SERVICE = 1001
        const val ID_RESULT = 1002
    }
}
