package dev.arison.codecmanager.presence

import android.companion.AssociationInfo
import android.companion.CompanionDeviceService
import dev.arison.codecmanager.CodecApp
import dev.arison.codecmanager.data.EventLog

/**
 * Bound by the system while an associated device is present. This is the lowest-power and most
 * reliable "device connected" trigger, and it works without a foreground service.
 */
class CompanionPresenceService : CompanionDeviceService() {

    override fun onDeviceAppeared(associationInfo: AssociationInfo) {
        val app = application as? CodecApp ?: return
        val mac = app.container.associations.associationMac(associationInfo) ?: return
        app.container.log.d(TAG, "CDM appeared mac=$mac")
        app.container.coordinator.onDeviceAppeared(mac)
    }

    override fun onDeviceDisappeared(associationInfo: AssociationInfo) {
        val app = application as? CodecApp ?: return
        val mac = app.container.associations.associationMac(associationInfo) ?: return
        app.container.log.d(TAG, "CDM disappeared mac=$mac")
        app.container.coordinator.onDeviceDisconnected(mac)
    }

    private companion object {
        const val TAG = "Presence"
    }
}
