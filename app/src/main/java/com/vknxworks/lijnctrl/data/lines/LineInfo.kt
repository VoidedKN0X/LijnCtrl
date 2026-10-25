package com.vknxworks.lijnctrl.data.lines

import android.content.Context
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.api.LineKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File

/** What a line looks like to passengers: the number on the bus, bus or tram, and its colours. */
@Serializable
data class LineInfo(
    val entity: String,
    val line: String,
    /** The number on the bus or tram, e.g. "T1" or "71". */
    val publicNumber: String,
    val description: String? = null,
    /** BUS, TRAM, METRO … as sent by De Lijn. */
    val transportType: String? = null,
    /** Hex colours without '#', e.g. "FFCC11". */
    val foreground: String? = null,
    val background: String? = null,
    val backgroundBorder: String? = null,
    val fetchedAt: Long = 0L,
) {
    val key: LineKey get() = LineKey(entity, line)
    val isTram: Boolean get() = transportType.equals("TRAM", ignoreCase = true)
}

@Serializable
private data class LineCacheFile(val lines: List<LineInfo> = emptyList(), val colors: Map<String, String> = emptyMap(), val colorsAt: Long = 0L)

/**
 * Line numbers, types and colours, fetched in batches and kept on the phone for a week. Departures only say
 * which internal line they belong to, so this is what turns "lijnnummer 7" into a yellow "T1" badge.
 */
class LineRepository(context: Context, private val api: DeLijnApi) {
    private val file = File(context.filesDir, "lines.json")
    private val mutex = Mutex()
    private val _lines = MutableStateFlow<Map<String, LineInfo>>(emptyMap())

    /** Known lines by "entity_line". */
    val lines: StateFlow<Map<String, LineInfo>> = _lines.asStateFlow()

    private var colors: Map<String, String> = emptyMap()
    private var colorsAt = 0L
    private var loaded = false

    /** Lines that just failed to load aren't asked for again for a few minutes. */
    private val failedAt = mutableMapOf<String, Long>()

    fun get(key: LineKey): LineInfo? = _lines.value[key.toString()]

    private suspend fun loadFromDisk() {
        if (loaded) return
        loaded = true
        withContext(Dispatchers.IO) {
            runCatching {
                if (file.exists()) {
                    val cache = DeLijnApi.JSON.decodeFromString(LineCacheFile.serializer(), file.readText())
                    _lines.value = cache.lines.associateBy { it.key.toString() }
                    colors = cache.colors
                    colorsAt = cache.colorsAt
                }
            }
        }
    }

    private suspend fun saveToDisk() = withContext(Dispatchers.IO) {
        runCatching {
            val tmp = File(file.parentFile, "lines.json.tmp")
            tmp.writeText(DeLijnApi.JSON.encodeToString(LineCacheFile.serializer(), LineCacheFile(_lines.value.values.toList(), colors, colorsAt)))
            tmp.renameTo(file)
        }
    }

    /** Makes sure [keys] are known (and not older than a week). Failures leave the badge plain; they never throw. */
    suspend fun ensure(keys: Collection<LineKey>) = mutex.withLock {
        loadFromDisk()
        val now = System.currentTimeMillis()
        val missing = keys.distinct().filter { k ->
            val known = _lines.value[k.toString()]
            val stale = known == null || now - known.fetchedAt > MAX_AGE_MS
            val recentlyFailed = failedAt[k.toString()]?.let { now - it < RETRY_AFTER_MS } == true
            stale && !recentlyFailed
        }
        if (missing.isEmpty()) return@withLock
        runCatching {
            if (colors.isEmpty() || now - colorsAt > MAX_AGE_MS) {
                colors = api.colors().mapNotNull { c -> c.hex?.let { c.code to it.removePrefix("#") } }.toMap()
                colorsAt = now
            }
        }
        val fetched = mutableMapOf<String, LineInfo>()
        missing.chunked(BATCH).forEach { batch ->
            runCatching {
                val info = api.lines(batch).associateBy { LineKey(it.entity, it.line).toString() }
                val colorCodes = runCatching { api.lineColors(batch) }.getOrDefault(emptyList())
                    .associateBy { LineKey(it.line.entity, it.line.line).toString() }
                batch.forEach { k ->
                    val l = info[k.toString()] ?: return@forEach
                    val cc = colorCodes[k.toString()]?.colors
                    fetched[k.toString()] = LineInfo(
                        entity = k.entity,
                        line = k.line,
                        publicNumber = l.publicNumber?.takeIf { it.isNotBlank() } ?: k.line,
                        description = l.description,
                        transportType = l.transportType,
                        foreground = cc?.foreground?.code?.let(colors::get),
                        background = cc?.background?.code?.let(colors::get),
                        backgroundBorder = cc?.backgroundBorder?.code?.let(colors::get),
                        fetchedAt = now,
                    )
                }
            }.onFailure { batch.forEach { k -> failedAt[k.toString()] = now } }
        }
        if (fetched.isNotEmpty()) {
            _lines.update { it + fetched }
            saveToDisk()
        }
    }

    /**
     * Lines found by the search API already come with their public number and colours, so they're kept
     * straight away (the transport type is filled in later, when the line is looked up fully).
     */
    suspend fun remember(found: List<com.vknxworks.lijnctrl.data.api.ApiSearchLineDirection>) = mutex.withLock {
        loadFromDisk()
        val now = System.currentTimeMillis()
        val added = found.distinctBy { LineKey(it.entity, it.line).toString() }.mapNotNull { d ->
            val key = LineKey(d.entity, d.line).toString()
            if (_lines.value.containsKey(key)) return@mapNotNull null
            key to LineInfo(
                entity = d.entity,
                line = d.line,
                publicNumber = d.publicNumber?.takeIf { it.isNotBlank() } ?: d.line,
                description = d.description,
                foreground = d.foreground?.removePrefix("#"),
                background = d.background?.removePrefix("#"),
                backgroundBorder = d.backgroundBorder?.removePrefix("#"),
                // Old enough that a full lookup still adds the transport type, new enough to show right away.
                fetchedAt = 0L,
            )
        }.toMap()
        if (added.isNotEmpty()) _lines.update { it + added }
    }

    /** Forgets every line (used when the app is reset). */
    suspend fun clear() = mutex.withLock {
        _lines.value = emptyMap()
        colors = emptyMap()
        colorsAt = 0L
        failedAt.clear()
        withContext(Dispatchers.IO) { runCatching { file.delete() } }
    }

    private companion object {
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
        const val RETRY_AFTER_MS = 5L * 60 * 1000
        const val BATCH = 10
    }
}
