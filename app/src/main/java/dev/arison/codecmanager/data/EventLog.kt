package dev.arison.codecmanager.data

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class LogEntry(
    val timestamp: Long,
    val level: String,
    val tag: String,
    val message: String,
)

class EventLog(context: Context) {

    private val appContext = context.applicationContext
    private val file = File(appContext.filesDir, "events.jsonl")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    init {
        scope.launch {
            val loaded = runCatching {
                if (!file.exists()) return@runCatching emptyList()
                file.readLines().takeLast(MAX_ENTRIES).mapNotNull { line ->
                    runCatching { json.decodeFromString<LogEntry>(line) }.getOrNull()
                }
            }.getOrDefault(emptyList())
            _entries.value = loaded
        }
    }

    fun d(tag: String, message: String) = log("D", tag, message)

    fun i(tag: String, message: String) = log("I", tag, message)

    fun w(tag: String, message: String) = log("W", tag, message)

    fun e(tag: String, message: String) = log("E", tag, message)

    fun log(level: String, tag: String, message: String) {
        val entry = LogEntry(System.currentTimeMillis(), level, tag, message)
        _entries.value = (_entries.value + entry).takeLast(MAX_ENTRIES)
        scope.launch {
            mutex.withLock {
                runCatching {
                    file.appendText(json.encodeToString(entry) + "\n")
                    if (file.length() > MAX_FILE_BYTES) {
                        val tail = file.readLines().takeLast(MAX_ENTRIES)
                        file.writeText(tail.joinToString("\n", postfix = "\n"))
                    }
                }
            }
        }
    }

    fun asText(): String = _entries.value.joinToString("\n") { entry ->
        "${timeFormat.format(Date(entry.timestamp))} ${entry.level}/${entry.tag}: ${entry.message}"
    }

    fun clear() {
        _entries.value = emptyList()
        scope.launch { mutex.withLock { runCatching { file.delete() } } }
    }

    private companion object {
        const val MAX_ENTRIES = 500
        const val MAX_FILE_BYTES = 512L * 1024L
    }
}
