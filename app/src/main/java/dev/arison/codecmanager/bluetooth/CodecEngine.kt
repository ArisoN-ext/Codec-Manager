package dev.arison.codecmanager.bluetooth

import android.bluetooth.BluetoothDevice

interface CodecEngine {
    fun isAvailable(): Boolean
    fun diagnostics(): String
    suspend fun readStatus(device: BluetoothDevice): Result<CodecStatusSnapshot>
    suspend fun apply(
        device: BluetoothDevice,
        target: CodecTarget,
        policy: ApplyPolicy = ApplyPolicy.Default,
    ): ApplyResult
    suspend fun isConnected(device: BluetoothDevice): Boolean
    suspend fun connectedDevices(): List<BluetoothDevice>
}
