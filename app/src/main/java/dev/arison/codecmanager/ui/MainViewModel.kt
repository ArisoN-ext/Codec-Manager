package dev.arison.codecmanager.ui

import android.Manifest
import android.app.Application
import android.app.LocaleManager
import android.bluetooth.BluetoothManager
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.LocaleList
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.arison.codecmanager.CodecApp
import dev.arison.codecmanager.bluetooth.CodecStatusSnapshot
import dev.arison.codecmanager.bluetooth.FailureReason
import dev.arison.codecmanager.bluetooth.LdacQuality
import dev.arison.codecmanager.data.AppSettings
import dev.arison.codecmanager.data.DeviceProfile
import dev.arison.codecmanager.data.LogEntry
import dev.arison.codecmanager.data.ThemeMode
import dev.arison.codecmanager.service.CodecForegroundService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PairedDeviceUi(
    val mac: String,
    val name: String,
    val associated: Boolean,
    val connected: Boolean,
)

data class ActiveDeviceUi(
    val mac: String,
    val name: String,
    val status: CodecStatusSnapshot,
)

data class DiagnosticsUi(
    val bluetoothPermission: Boolean = false,
    val notificationsPermission: Boolean = false,
    val adapterOn: Boolean = false,
    val apiAvailable: Boolean = false,
    val apiDiagnostics: String = "",
    val associatedCount: Int = 0,
    val connectedName: String? = null,
    val batteryOptimized: Boolean = true,
    val androidVersion: String = "",
    val manufacturer: String = "",
    val privileged: Boolean = false,
    val hasCompanionSupport: Boolean = true,
)

data class UiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val profiles: List<DeviceProfile> = emptyList(),
    val pairedDevices: List<PairedDeviceUi> = emptyList(),
    val activeDevice: ActiveDeviceUi? = null,
    val activeError: FailureReason? = null,
    val applying: Boolean = false,
    val lastEvent: dev.arison.codecmanager.ApplyEvent? = null,
    val log: List<LogEntry> = emptyList(),
    val diagnostics: DiagnosticsUi = DiagnosticsUi(),
    val toast: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val container = (application as CodecApp).container
    private val associations = container.associations
    private val settingsRepo = container.settings
    private val engine = container.engine
    private val adapter = application.getSystemService(BluetoothManager::class.java)?.adapter

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepo.settings,
                settingsRepo.profiles,
                container.log.entries,
            ) { settings, profiles, log ->
                Triple(settings, profiles, log)
            }.collect { (settings, profiles, log) ->
                _state.update { it.copy(settings = settings, profiles = profiles, log = log) }
            }
        }
        viewModelScope.launch {
            container.coordinator.events.collect { event ->
                _state.update { it.copy(lastEvent = event) }
            }
        }
        viewModelScope.launch {
            while (isActive) {
                refresh()
                _state.update { it.copy(loading = false) }
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    fun refreshNow() {
        viewModelScope.launch { refresh() }
    }

    private suspend fun refresh() {
        val context = getApplication<Application>()
        val hasBluetooth = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        val hasNotifications = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val adapterOn = adapter?.isEnabled == true

        val assocMacs = associations.associatedMacs()
        val connected = if (hasBluetooth) engine.connectedDevices() else emptyList()
        val connectedMacs = connected.mapNotNull { device ->
            runCatching { device.address.uppercase() }.getOrNull()
        }.toSet()

        val paired = if (hasBluetooth) {
            associations.pairedDevices()
                .map { device ->
                    val mac = runCatching { device.address.uppercase() }.getOrNull() ?: return@map null
                    PairedDeviceUi(
                        mac = mac,
                        name = associations.deviceName(device),
                        associated = assocMacs.any { it.equals(mac, ignoreCase = true) },
                        connected = mac in connectedMacs,
                    )
                }
                .filterNotNull()
                .sortedBy { it.name.lowercase() }
        } else {
            emptyList()
        }

        var active: ActiveDeviceUi? = null
        var activeError: FailureReason? = null
        val activeDevice = connected.firstOrNull()
        if (activeDevice != null) {
            val mac = runCatching { activeDevice.address.uppercase() }.getOrNull()
            val name = associations.deviceName(activeDevice)
            val result = engine.readStatus(activeDevice)
            val status = result.getOrNull()
            if (status != null && mac != null) {
                active = ActiveDeviceUi(mac = mac, name = name, status = status)
            } else {
                activeError = (result.exceptionOrNull() as? dev.arison.codecmanager.bluetooth.CodecFailure)?.reason
                    ?: FailureReason.UNKNOWN
            }
        }

        val powerManager = context.getSystemService(PowerManager::class.java)
        val batteryOptimized = powerManager?.isIgnoringBatteryOptimizations(context.packageName) != true
        val appInfo = context.applicationInfo
        val privileged = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

        val diagnostics = DiagnosticsUi(
            bluetoothPermission = hasBluetooth,
            notificationsPermission = hasNotifications,
            adapterOn = adapterOn,
            apiAvailable = engine.isAvailable(),
            apiDiagnostics = engine.diagnostics(),
            associatedCount = assocMacs.size,
            connectedName = active?.name,
            batteryOptimized = batteryOptimized,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            manufacturer = "${Build.MANUFACTURER} ${Build.MODEL}",
            privileged = privileged,
            hasCompanionSupport = context.packageManager.hasSystemFeature(
                PackageManager.FEATURE_COMPANION_DEVICE_SETUP,
            ),
        )

        _state.update {
            it.copy(
                pairedDevices = paired,
                activeDevice = active,
                activeError = activeError,
                diagnostics = diagnostics,
            )
        }
    }

    fun bindAssociationLauncher(launcher: (android.content.IntentSender) -> Unit) {
        associations.pendingIntentLauncher = launcher
    }

    fun onAssociationActivityResult() {
        refreshNow()
    }

    fun completeOnboarding() {
        viewModelScope.launch {
            settingsRepo.setOnboardingDone()
            if (_state.value.settings.autoSwitch) {
                CodecForegroundService.start(getApplication())
            }
        }
    }

    fun associate(mac: String) {
        viewModelScope.launch {
            val device = runCatching { adapter?.getRemoteDevice(mac) }.getOrNull() ?: return@launch
            val result = associations.associate(device)
            container.log.i("Assoc", "associate $mac: ${result.isSuccess}")
            result.exceptionOrNull()?.let { container.log.e("Assoc", it.message ?: "failed") }
            refresh()
        }
    }

    fun disassociate(mac: String) {
        viewModelScope.launch {
            associations.disassociate(mac)
            container.log.i("Assoc", "disassociated $mac")
            refresh()
        }
    }

    fun applyQuality(mac: String, quality: LdacQuality) {
        viewModelScope.launch {
            _state.update { it.copy(applying = true) }
            container.coordinator.applyNow(mac, quality)
            _state.update { it.copy(applying = false) }
            refresh()
        }
    }

    fun testApply() {
        val active = _state.value.activeDevice ?: return
        val quality = active.status.current.ldacQuality.takeIf { it != LdacQuality.DEFAULT }
            ?: LdacQuality.AUDIO_QUALITY
        applyQuality(active.mac, quality)
    }

    fun saveProfile(profile: DeviceProfile) {
        viewModelScope.launch {
            settingsRepo.upsertProfile(profile)
            container.log.i("Profile", "saved ${profile.name}")
            refresh()
        }
    }

    fun deleteProfile(mac: String) {
        viewModelScope.launch {
            settingsRepo.removeProfile(mac)
            container.log.i("Profile", "removed $mac")
        }
    }

    fun setProfileEnabled(profile: DeviceProfile, enabled: Boolean) {
        saveProfile(profile.copy(enabled = enabled))
    }

    fun setApplyOnConnect(profile: DeviceProfile, enabled: Boolean) {
        saveProfile(profile.copy(applyOnConnect = enabled))
    }

    fun setDeviceAutoApply(mac: String, name: String, enabled: Boolean) {
        val existing = _state.value.profiles.firstOrNull { it.mac.equals(mac, ignoreCase = true) }
            ?: DeviceProfile(mac = mac, name = name)
        saveProfile(existing.copy(applyOnConnect = enabled, enabled = true))
    }

    fun setAutoSwitch(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setAutoSwitch(enabled)
            val context = getApplication<Application>()
            if (enabled) CodecForegroundService.start(context) else CodecForegroundService.stop(context)
            container.log.i("Settings", "auto-switch=$enabled")
        }
    }

    fun setStartOnBoot(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(startOnBoot = enabled) } }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(notifications = enabled) } }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepo.update { it.copy(themeMode = mode) } }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(dynamicColor = enabled) } }
    }

    fun setReduceMotion(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.update { it.copy(reduceMotion = enabled) } }
    }

    fun setLanguage(tag: String?) {
        viewModelScope.launch {
            settingsRepo.update { it.copy(languageTag = tag) }
            if (Build.VERSION.SDK_INT >= 33) {
                val localeManager = getApplication<Application>().getSystemService(LocaleManager::class.java)
                localeManager?.applicationLocales =
                    if (tag.isNullOrBlank()) LocaleList.getEmptyLocaleList()
                    else LocaleList.forLanguageTags(tag)
            }
        }
    }

    fun clearLog() {
        container.log.clear()
    }

    fun exportLog(): String {
        val diagnostics = _state.value.diagnostics
        return buildString {
            appendLine("Codec Manager diagnostics")
            appendLine("Android: ${diagnostics.androidVersion}")
            appendLine("Device: ${diagnostics.manufacturer}")
            appendLine("API methods: ${diagnostics.apiDiagnostics}")
            appendLine("Associated: ${diagnostics.associatedCount}")
            appendLine("Privileged: ${diagnostics.privileged}")
            appendLine("----")
            append(container.log.asText())
        }
    }

    fun dismissToast() {
        _state.update { it.copy(toast = null) }
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 3_000L
    }
}
