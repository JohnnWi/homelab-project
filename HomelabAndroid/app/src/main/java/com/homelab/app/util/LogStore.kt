package com.homelab.app.util

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MAX_LOG_ENTRIES = 500

enum class LogLevel { DEBUG, INFO, WARN, NET }

data class LogEntry(
    val id: Long,
    val timestamp: Long,
    val level: LogLevel,
    val tag: String,
    val message: String
)

object LogStore {
    private val lock = Any()
    private val buffer = ArrayDeque<LogEntry>(MAX_LOG_ENTRIES)
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> = _logs.asStateFlow()

    private val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
        .withZone(ZoneId.systemDefault())

    private val scope = CoroutineScope(Dispatchers.Default)
    private var updateJob: Job? = null
    private var nextId = java.util.concurrent.atomic.AtomicLong(0)

    fun add(level: LogLevel, tag: String, message: String) {
        val entry = LogEntry(
            id = nextId.getAndIncrement(),
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message
        )

        synchronized(lock) {
            if (buffer.size >= MAX_LOG_ENTRIES) {
                buffer.removeFirst()
            }
            buffer.addLast(entry)
            
            if (updateJob?.isActive != true) {
                updateJob = scope.launch(Dispatchers.Default) {
                    delay(250)
                    val newList = synchronized(lock) { buffer.toList() }
                    _logs.value = newList
                }
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            buffer.clear()
            _logs.value = emptyList()
        }
    }

    fun formatForCopy(): String = synchronized(lock) {
        buffer.joinToString("\n") { entry ->
            val time = formatter.format(Instant.ofEpochMilli(entry.timestamp))
            "[$time] ${entry.level.name} ${entry.tag}: ${entry.message}"
        }
    }
}
