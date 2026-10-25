package com.vknxworks.lijnctrl.data.disruptions

import com.vknxworks.lijnctrl.data.api.ApiDisruption
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.api.Departure
import com.vknxworks.lijnctrl.data.api.Disruption
import com.vknxworks.lijnctrl.data.api.LineKey
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.api.localized
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

/**
 * Disruptions (storingen) and diversions (omleidingen) at stops and on line directions, kept in memory for a
 * few minutes. They change rarely, while departure boards refresh every 30 seconds, so this keeps the number of
 * calls to De Lijn down: each stop or line direction is asked at most once every [TTL_MS]. A failed lookup
 * just means no disruptions are shown (and it's tried again sooner).
 */
class DisruptionRepository(private val api: DeLijnApi) {
    private class Entry(val at: Long, val ttl: Long, val list: List<ApiDisruption>)

    private val mutex = Mutex()
    private val cache = mutableMapOf<String, Entry>()
    /** At most a few lookups at once, so a board with many lines doesn't fire a burst of calls. */
    private val gate = Semaphore(3)

    private suspend fun cached(key: String, fetch: suspend () -> List<ApiDisruption>): List<ApiDisruption> {
        val now = System.currentTimeMillis()
        mutex.withLock { cache[key]?.takeIf { now - it.at < it.ttl }?.let { return it.list } }
        val result = gate.withPermit { runCatching { fetch() } }
        val list = result.getOrDefault(emptyList())
        mutex.withLock {
            cache.entries.removeAll { now - it.value.at > it.value.ttl }
            cache[key] = Entry(now, if (result.isSuccess) TTL_MS else RETRY_MS, list)
        }
        return list
    }

    /** Disruptions and diversions at [stop] today. */
    suspend fun atStop(stop: StopKey): List<ApiDisruption> = cached("s:$stop") {
        coroutineScope {
            val dis = async { runCatching { api.stopDisruptions(stop) } }
            val div = async { runCatching { api.stopDiversions(stop) } }
            val a = dis.await()
            val b = div.await()
            if (a.isFailure && b.isFailure) throw a.exceptionOrNull()!!
            (a.getOrDefault(emptyList()) + b.getOrDefault(emptyList())).distinctBy { it.title to it.description }
        }
    }

    /** Disruptions and diversions on one direction of a line today. */
    suspend fun onLine(line: LineKey, direction: String): List<ApiDisruption> = cached("l:$line:${direction.uppercase()}") {
        api.lineDisruptions(line, direction).distinctBy { it.title to it.description }
    }

    /**
     * Per departure (by [Departure.matchKey]): the disruptions that affect it. From the stop's own list (the ones
     * naming its line, or the whole stop), plus, with [withLines], everything reported on its line direction.
     */
    suspend fun forDepartures(
        stop: StopKey,
        departures: List<Departure>,
        language: String,
        withLines: Boolean,
    ): Map<String, List<Disruption>> = coroutineScope {
        val atStop = async { atStop(stop).map { it.localized(language) } }
        val directions = if (withLines) departures.mapNotNull { d -> d.line?.let { l -> d.direction?.let { l to it } } }.distinct() else emptyList()
        val onLines = directions.map { (l, dir) -> async { (l to dir.uppercase()) to onLine(l, dir).map { it.localized(language) } } }
        val stopList = atStop.await()
        val lineMap = onLines.awaitAll().toMap()
        departures.associate { d ->
            val own = stopList.filter { it.affects(d) }
            val line = d.line?.let { l -> d.direction?.let { lineMap[l to it.uppercase()] } }.orEmpty()
            d.matchKey to (own + line).distinctBy { it.identity }
        }.filterValues { it.isNotEmpty() }
    }

    private companion object {
        const val TTL_MS = 10L * 60 * 1000
        const val RETRY_MS = 2L * 60 * 1000
    }
}
