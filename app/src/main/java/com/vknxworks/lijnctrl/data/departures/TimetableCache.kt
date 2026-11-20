package com.vknxworks.lijnctrl.data.departures

import com.vknxworks.lijnctrl.data.api.BRUSSELS
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.api.Departure
import com.vknxworks.lijnctrl.data.api.LineKey
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.crypto.DataKeyStore
import com.vknxworks.lijnctrl.data.crypto.SealedFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Instant
import java.time.LocalDate

@Serializable
private data class CachedDeparture(
    val id: String,
    val stop: String? = null,
    val entity: String? = null,
    val line: String? = null,
    val direction: String? = null,
    val trip: String? = null,
    val serviceDate: String? = null,
    val destination: String = "",
    val vias: List<String> = emptyList(),
    val scheduled: Long,
    val predicted: Long? = null,
    val cancelled: Boolean = false,
    val live: Boolean = false,
)

@Serializable
private data class CachedTimetable(
    val entity: String,
    val number: String,
    val date: String,
    val fetchedAt: Long,
    val departures: List<CachedDeparture> = emptyList(),
)

@Serializable
private data class TimetableCacheFile(val timetables: List<CachedTimetable> = emptyList())

/**
 * Day timetables of your saved stops, kept on the phone for a few hours ([TimetableCacheRules.MAX_AGE_MS]), so a
 * fresh start of the app doesn't download the whole day's timetable for every saved stop again.
 *
 * It's a cache and nothing more:
 * - only stops [persist] says yes to (your saved stops, which the encrypted saved file already names) are kept,
 *   so the file says nothing about other stops you looked at;
 * - it's only ever written encrypted ([SealedFile], with the data key that also seals the saved file and the
 *   line cache). Without a data key nothing is written, and this never makes a key;
 * - a file that can't be opened or read is deleted and the timetables are fetched again.
 */
class TimetableCache(
    private val file: File,
    private val keys: DataKeyStore,
    private val scope: CoroutineScope,
    private val persist: (StopKey) -> Boolean,
) {
    private class Entry(val fetchedAt: Long, val departures: List<Departure>)

    private val mutex = Mutex()
    private val entries = HashMap<Pair<StopKey, LocalDate>, Entry>()
    private var loaded = false
    private var saveJob: Job? = null
    /** One writer at a time. */
    private val writing = Any()

    /** The kept timetable of [stop] on [date] when there's one that may still be used, else null. */
    suspend fun get(stop: StopKey, date: LocalDate): List<Departure>? = mutex.withLock {
        load()
        val e = entries[stop to date] ?: return@withLock null
        if (TimetableCacheRules.usable(e.fetchedAt, date, System.currentTimeMillis(), LocalDate.now(BRUSSELS))) e.departures else null
    }

    /** Keeps a timetable that was just fetched (only for a saved stop); it's written to the phone a moment later. */
    suspend fun put(stop: StopKey, date: LocalDate, departures: List<Departure>) {
        if (!persist(stop)) return
        mutex.withLock {
            load()
            entries[stop to date] = Entry(System.currentTimeMillis(), departures)
            scheduleSave()
        }
    }

    /** A stop was removed from the saved stops: its timetables leave the file too (a few seconds later). */
    suspend fun forgetUnsaved() = mutex.withLock {
        load()
        if (entries.keys.any { !persist(it.first) }) scheduleSave()
    }

    /** Several timetables arrive together at start: one write a few seconds after the last of them. */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(SAVE_DELAY_MS)
            save()
        }
    }

    /** Reads the file once. Call with [mutex] held. */
    private suspend fun load() {
        if (loaded) return
        loaded = true
        val leftOut = withContext(Dispatchers.IO) { runCatching { read() }.getOrDefault(false) }
        // Something in the file was too old or isn't a saved stop any more: write the file without it.
        if (leftOut) scheduleSave()
    }

    /** Fills [entries] from the file; true when the file held something that's no longer wanted. */
    private fun read(): Boolean {
        if (!file.exists()) return false
        val cache = runCatching {
            if (file.length() > TimetableCacheRules.MAX_FILE_BYTES) return@runCatching null
            val bytes = file.readBytes()
            // Only ever an encrypted file; the key is never made here.
            if (!SealedFile.isSealed(bytes)) return@runCatching null
            val key = keys.get(create = false) ?: return@runCatching null
            DeLijnApi.JSON.decodeFromString(TimetableCacheFile.serializer(), String(SealedFile.open(key, bytes), Charsets.UTF_8))
        }.getOrNull()
        if (cache == null) {
            // Only a cache: one that can't be opened is removed and built up again.
            file.delete()
            return false
        }
        val now = System.currentTimeMillis()
        val today = LocalDate.now(BRUSSELS)
        var leftOut = false
        for (t in cache.timetables) {
            val stop = StopKey(t.entity, t.number)
            val date = runCatching { LocalDate.parse(t.date) }.getOrNull()
            val list = runCatching { t.departures.map { it.toDeparture() } }.getOrNull()
            if (date == null || list == null || !TimetableCacheRules.usable(t.fetchedAt, date, now, today) || !persist(stop)) {
                leftOut = true
                continue
            }
            entries[stop to date] = Entry(t.fetchedAt, list)
        }
        return leftOut
    }

    private suspend fun save() {
        val snapshot = mutex.withLock {
            val now = System.currentTimeMillis()
            val today = LocalDate.now(BRUSSELS)
            entries.entries.removeAll { (k, e) -> !TimetableCacheRules.usable(e.fetchedAt, k.second, now, today) || !persist(k.first) }
            entries.map { (k, e) -> CachedTimetable(k.first.entity, k.first.number, k.second.toString(), e.fetchedAt, e.departures.map { it.toCached() }) }
        }
        withContext(Dispatchers.IO) {
            runCatching {
                synchronized(writing) {
                    if (snapshot.isEmpty()) {
                        file.delete()
                        return@runCatching
                    }
                    // Never as an ordinary file: without a data key the timetables just stay in memory, as before.
                    val key = keys.get(create = false) ?: return@runCatching
                    val plain = DeLijnApi.JSON.encodeToString(TimetableCacheFile.serializer(), TimetableCacheFile(snapshot)).toByteArray(Charsets.UTF_8)
                    val sealed = SealedFile.sealChecked(key, plain) ?: return@runCatching
                    file.parentFile?.mkdirs()
                    SealedFile.writeAtomic(file, File(file.parentFile, file.name + ".tmp"), sealed)
                }
            }
        }
    }

    private fun Departure.toCached() = CachedDeparture(
        id = id,
        stop = stop,
        entity = line?.entity,
        line = line?.line,
        direction = direction,
        trip = trip,
        serviceDate = serviceDate?.toString(),
        destination = destination,
        vias = vias,
        scheduled = scheduled.toEpochMilli(),
        predicted = predicted?.toEpochMilli(),
        cancelled = cancelled,
        live = live,
    )

    private fun CachedDeparture.toDeparture() = Departure(
        id = id,
        stop = stop,
        line = if (entity != null && line != null) LineKey(entity, line) else null,
        direction = direction,
        trip = trip,
        serviceDate = serviceDate?.let { LocalDate.parse(it) },
        destination = destination,
        vias = vias,
        scheduled = Instant.ofEpochMilli(scheduled),
        predicted = predicted?.let { Instant.ofEpochMilli(it) },
        cancelled = cancelled,
        live = live,
    )

    private companion object {
        const val SAVE_DELAY_MS = 4_000L
    }
}
