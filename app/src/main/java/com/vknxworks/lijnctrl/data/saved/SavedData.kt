package com.vknxworks.lijnctrl.data.saved

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.appDataStore
import com.vknxworks.lijnctrl.data.crypto.DataKeyStore
import com.vknxworks.lijnctrl.data.favourites.SavedLine
import com.vknxworks.lijnctrl.data.favourites.SavedStop
import com.vknxworks.lijnctrl.data.plan.PlanHistoryEntry
import com.vknxworks.lijnctrl.data.plan.SavedPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/**
 * Everything LijnCtrl remembers about where you travel: saved stops and lines, the lines you chose to see per
 * stop, recent routes, Home and Work, and which disruptions you were already told about (their ids name your
 * saved stops and lines). Kept only on this phone, in one encrypted file (`saved.json`, see [SealedStore]).
 */
@Serializable
data class SavedData(
    val stops: List<SavedStop> = emptyList(),
    val lines: List<SavedLine> = emptyList(),
    /** Stop (as "entity_number") to the lines shown there (as "entity_line"), sorted. */
    val filters: Map<String, List<String>> = emptyMap(),
    /** Recent routes, newest first. */
    val history: List<PlanHistoryEntry> = emptyList(),
    val home: SavedPoint? = null,
    val work: SavedPoint? = null,
    /** Disruptions already notified about (disruption alerts). */
    val seen: Set<String> = emptySet(),
)

typealias SavedStore = SealedStore<SavedData>

/** The saved data's store: `filesDir/saved.json`, encrypted with the data key in [keys]. */
fun savedStore(context: Context, scope: CoroutineScope, keys: DataKeyStore): SavedStore = SealedStore(
    dir = context.filesDir,
    name = "saved.json",
    keys = keys,
    old = SettingsStoreHome(context.appDataStore),
    empty = SavedData(),
    encode = { DeLijnApi.JSON.encodeToString(SavedData.serializer(), it).toByteArray(Charsets.UTF_8) },
    decode = { DeLijnApi.JSON.decodeFromString(SavedData.serializer(), String(it, Charsets.UTF_8)) },
    scope = scope,
)

/**
 * The saved data as it was kept before it was encrypted: seven values in the settings store (DataStore), next to the
 * settings. Still read and written this way on a phone that has no data key; otherwise read once, moved into
 * the encrypted file and removed here (the settings themselves stay).
 */
private class SettingsStoreHome(private val store: DataStore<Preferences>) : OldHome<SavedData> {
    private val stops = stringPreferencesKey("saved_stops")
    private val lines = stringPreferencesKey("saved_lines")
    private val filters = stringPreferencesKey("line_filters")
    private val history = stringPreferencesKey("plan_history")
    private val home = stringPreferencesKey("place_home")
    private val work = stringPreferencesKey("place_work")
    private val seen = stringSetPreferencesKey("disruptions_seen")

    private val stopList = ListSerializer(SavedStop.serializer())
    private val lineList = ListSerializer(SavedLine.serializer())
    private val filterMap = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
    private val historyList = ListSerializer(PlanHistoryEntry.serializer())

    override suspend fun read(): OldHome.Found<SavedData> {
        val p = store.data.first()
        var complete = true
        fun <T> dec(raw: String?, serializer: KSerializer<T>): T? {
            if (raw == null) return null
            return try {
                DeLijnApi.JSON.decodeFromString(serializer, raw)
            } catch (e: Exception) {
                complete = false
                null
            }
        }
        val data = SavedData(
            stops = dec(p[stops], stopList) ?: emptyList(),
            lines = dec(p[lines], lineList) ?: emptyList(),
            filters = dec(p[filters], filterMap)?.mapValues { it.value.distinct().sorted() } ?: emptyMap(),
            history = dec(p[history], historyList) ?: emptyList(),
            home = dec(p[home], SavedPoint.serializer()),
            work = dec(p[work], SavedPoint.serializer()),
            seen = p[seen]?.toSet() ?: emptySet(),
        )
        val present = p.contains(stops) || p.contains(lines) || p.contains(filters) || p.contains(history) ||
            p.contains(home) || p.contains(work) || p.contains(seen)
        return OldHome.Found(data, present, complete)
    }

    /** Only the values that changed are written, each the way it always was. */
    override suspend fun write(prev: SavedData, next: SavedData) {
        store.edit { p ->
            if (next.stops != prev.stops) p[stops] = DeLijnApi.JSON.encodeToString(stopList, next.stops)
            if (next.lines != prev.lines) p[lines] = DeLijnApi.JSON.encodeToString(lineList, next.lines)
            if (next.filters != prev.filters) {
                if (next.filters.isEmpty()) p.remove(filters) else p[filters] = DeLijnApi.JSON.encodeToString(filterMap, next.filters)
            }
            if (next.history != prev.history) {
                if (next.history.isEmpty()) p.remove(history) else p[history] = DeLijnApi.JSON.encodeToString(historyList, next.history)
            }
            if (next.home != prev.home) {
                val h = next.home
                if (h == null) p.remove(home) else p[home] = DeLijnApi.JSON.encodeToString(SavedPoint.serializer(), h)
            }
            if (next.work != prev.work) {
                val w = next.work
                if (w == null) p.remove(work) else p[work] = DeLijnApi.JSON.encodeToString(SavedPoint.serializer(), w)
            }
            if (next.seen != prev.seen) p[seen] = next.seen
        }
    }

    override suspend fun clear() {
        store.edit { p ->
            p.remove(stops)
            p.remove(lines)
            p.remove(filters)
            p.remove(history)
            p.remove(home)
            p.remove(work)
            p.remove(seen)
        }
    }
}
