package dev.arison.codecmanager.service

import android.app.Service
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.arison.codecmanager.CodecApp
import dev.arison.codecmanager.bluetooth.LdacQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class CodecForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var app: CodecApp

    private val a2dpReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java) ?: return
            val mac = runCatching { device.address.uppercase() }.getOrNull() ?: return
            val state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)
            when (intent.action) {
                BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED -> {
                    if (state == BluetoothProfile.STATE_CONNECTED) {
                        app.container.coordinator.onA2dpConnected(mac)
                    } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                        app.container.coordinator.onDeviceDisconnected(mac)
                    }
                }
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    app.container.coordinator.onDeviceAppeared(mac)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        app = application as CodecApp
        registerA2dpReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureForeground()
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_APPLY -> {
                val mac = intent.getStringExtra(EXTRA_MAC)
                val qualityCode = intent.getLongExtra(EXTRA_QUALITY, LdacQuality.AUDIO_QUALITY.code)
                if (mac != null) {
                    scope.launch {
                        app.container.coordinator.applyNow(
                            mac = mac,
                            quality = LdacQuality.fromCode(qualityCode),
                            source = "notification",
                        )
                        val autoSwitch = app.container.settings.settings.first().autoSwitch
                        if (!autoSwitch) stopSelf()
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(a2dpReceiver) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun ensureForeground() {
        val notification = app.container.notifier.serviceNotification()
        ServiceCompat.startForeground(
            this,
            CodecNotifier.ID_SERVICE,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
    }

    private fun registerA2dpReceiver() {
        val filter = IntentFilter().apply {
            addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        }
        ContextCompat.registerReceiver(
            this,
            a2dpReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
    }

    companion object {
        const val ACTION_START = "dev.arison.codecmanager.action.START"
        const val ACTION_STOP = "dev.arison.codecmanager.action.STOP"
        const val ACTION_APPLY = "dev.arison.codecmanager.action.APPLY"
        const val EXTRA_MAC = "extra_mac"
        const val EXTRA_QUALITY = "extra_quality"

        fun start(context: Context) {
            val intent = Intent(context, CodecForegroundService::class.java)
                .setAction(ACTION_START)
            runCatching { ContextCompat.startForegroundService(context, intent) }
        }

        fun stop(context: Context) {
            val intent = Intent(context, CodecForegroundService::class.java)
                .setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }
}
