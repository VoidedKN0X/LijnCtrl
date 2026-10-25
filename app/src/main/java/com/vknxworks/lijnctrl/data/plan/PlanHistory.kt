package com.vknxworks.lijnctrl.data.plan

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.appDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** One end of a remembered route. "My location" is kept as just that, never as a position. */
@Serializable
data class SavedPoint(
    val name: String,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val stopEntity: String? = null,
    val stopNumber: String? = null,
    val detail: String? = null,
    val isMyLocation: Boolean = false,
) {
    fun toPoint() = PlanPoint(
        name = name,
        latitude = if (isMyLocation) 0.0 else latitude,
        longitude = if (isMyLocation) 0.0 else longitude,
        stop = if (stopEntity != null && stopNumber != null) StopKey(stopEntity, stopNumber) else null,
        detail = detail,
        isMyLocation = isMyLocation,
    )

    /** Same place, whatever the name: the stop, the position (to about 10 m), or "my location". */
    val identity: String
        get() = when {
            isMyLocation -> "me"
            stopNumber != null -> "stop:$stopEntity/$stopNumber"
            else -> "at:${"%.4f".format(java.util.Locale.ROOT, latitude)},${"%.4f".format(java.util.Locale.ROOT, longitude)}"
        }

    companion object {
        fun of(p: PlanPoint) = SavedPoint(
            name = p.name,
            latitude = if (p.isMyLocation) 0.0 else p.latitude,
            longitude = if (p.isMyLocation) 0.0 else p.longitude,
            stopEntity = p.stop?.entity,
            stopNumber = p.stop?.number,
            detail = p.detail,
            isMyLocation = p.isMyLocation,
        )
    }
}

/** A route you planned: from, to, and when you last planned it. */
@Serializable
data class PlanHistoryEntry(val from: SavedPoint, val to: SavedPoint, val plannedAt: Long) {
    val key: String get() = "${from.identity}>${to.identity}"
}

/** Routes you planned, newest first. Kept only on this phone, and only while route history is on in Settings. */
class PlanHistoryRepository(private val context: Context, scope: CoroutineScope) {
    private val key = stringPreferencesKey("plan_history")
    private val serializer = ListSerializer(PlanHistoryEntry.serializer())

    private fun decode(raw: String?) = raw?.let { runCatching { DeLijnApi.JSON.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()

    val entries: StateFlow<List<PlanHistoryEntry>> = context.appDataStore.data
        .map { decode(it[key]) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    private suspend fun edit(transform: (List<PlanHistoryEntry>) -> List<PlanHistoryEntry>) {
        context.appDataStore.edit { p ->
            val next = transform(decode(p[key]))
            if (next.isEmpty()) p.remove(key) else p[key] = DeLijnApi.JSON.encodeToString(serializer, next)
        }
    }

    /** Puts the route at the top (moving it there if it was already in the list), keeping the newest [MAX]. */
    suspend fun add(from: PlanPoint, to: PlanPoint) {
        val entry = PlanHistoryEntry(SavedPoint.of(from), SavedPoint.of(to), System.currentTimeMillis())
        edit { list -> (listOf(entry) + list.filterNot { it.key == entry.key }).take(MAX) }
    }

    suspend fun remove(entry: PlanHistoryEntry) = edit { list -> list.filterNot { it.key == entry.key } }

    suspend fun clear() = edit { emptyList() }

    companion object {
        const val MAX = 15
    }
}

/** Home and Work, for one-tap routes on the Plan tab. Kept only on this phone. */
class SavedPlacesRepository(private val context: Context, scope: CoroutineScope) {
    enum class Slot(val key: String) { HOME("place_home"), WORK("place_work") }

    private fun decode(raw: String?): SavedPoint? = raw?.let { runCatching { DeLijnApi.JSON.decodeFromString(SavedPoint.serializer(), it) }.getOrNull() }

    val home: StateFlow<SavedPoint?> = context.appDataStore.data
        .map { decode(it[stringPreferencesKey(Slot.HOME.key)]) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val work: StateFlow<SavedPoint?> = context.appDataStore.data
        .map { decode(it[stringPreferencesKey(Slot.WORK.key)]) }
        .stateIn(scope, SharingStarted.Eagerly, null)

    /** Home or Work as saved right now, read from disk (the flows above may not have loaded yet in a fresh start). */
    suspend fun get(slot: Slot): SavedPoint? = decode(context.appDataStore.data.first()[stringPreferencesKey(slot.key)])

    suspend fun set(slot: Slot, point: PlanPoint?) {
        context.appDataStore.edit { p ->
            val k = stringPreferencesKey(slot.key)
            if (point == null || point.isMyLocation) p.remove(k)
            else p[k] = DeLijnApi.JSON.encodeToString(SavedPoint.serializer(), SavedPoint.of(point))
        }
    }
}
