package dev.arison.codecmanager.bluetooth

import java.util.concurrent.ConcurrentHashMap

/**
 * Some stacks report `codecSpecific1 == 0` (default) right after a successful change. Remembering
 * what we applied lets us avoid pointless re-applies and show a sensible state.
 */
object PlaybackQualityMemory {
    private val remembered = ConcurrentHashMap<String, ConcurrentHashMap<Int, Long>>()

    private fun key(address: String?) = address?.uppercase().orEmpty()

    fun remember(address: String?, codecType: Int, specific1: Long) {
        if (specific1 == 0L) return
        remembered.getOrPut(key(address)) { ConcurrentHashMap() }[codecType] = specific1
    }

    fun remembered(address: String?, codecType: Int): Long? =
        remembered[key(address)]?.get(codecType)

    fun forget(address: String?) {
        remembered.remove(key(address))
    }
}
