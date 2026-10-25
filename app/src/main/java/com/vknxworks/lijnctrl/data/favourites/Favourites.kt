package com.vknxworks.lijnctrl.data.favourites

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.appDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

/** A stop saved on the Stops tab. */
@Serializable
data class SavedStop(
    val entity: String,
    val number: String,
    val name: String,
    val municipality: String? = null,
    /** Your own name for it ("Home", "Work"), shown instead of the stop's name. */
    val nickname: String? = null,
) {
    val key: StopKey get() = StopKey(entity, number)
    val title: String get() = nickname?.takeIf { it.isNotBlank() } ?: name
}

/** Saved stops, in the order you put them. Kept only on this phone. */
class FavouritesRepository(private val context: Context, scope: CoroutineScope) {
    private val key = stringPreferencesKey("saved_stops")
    private val serializer = ListSerializer(SavedStop.serializer())

    val stops: StateFlow<List<SavedStop>> = context.appDataStore.data
        .map { p -> p[key]?.let { runCatching { DeLijnApi.JSON.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    fun isSaved(stop: StopKey, list: List<SavedStop> = stops.value) = list.any { it.key == stop }

    private suspend fun edit(transform: (List<SavedStop>) -> List<SavedStop>) {
        context.appDataStore.edit { p ->
            val current = p[key]?.let { runCatching { DeLijnApi.JSON.decodeFromString(serializer, it) }.getOrNull() } ?: emptyList()
            p[key] = DeLijnApi.JSON.encodeToString(serializer, transform(current))
        }
    }

    suspend fun add(stop: SavedStop) = edit { list -> if (list.any { it.key == stop.key }) list else list + stop }

    suspend fun remove(stop: StopKey) = edit { list -> list.filterNot { it.key == stop } }

    suspend fun toggle(stop: SavedStop) = edit { list ->
        if (list.any { it.key == stop.key }) list.filterNot { it.key == stop.key } else list + stop
    }

    suspend fun rename(stop: StopKey, nickname: String?) = edit { list ->
        list.map { if (it.key == stop) it.copy(nickname = nickname?.trim()?.takeIf(String::isNotEmpty)) else it }
    }

    /** Moves a stop one place up (-1) or down (+1). */
    suspend fun move(stop: StopKey, by: Int) = edit { list ->
        val i = list.indexOfFirst { it.key == stop }
        val j = i + by
        if (i < 0 || j !in list.indices) list
        else list.toMutableList().apply { add(j, removeAt(i)) }
    }

    /** Keeps the stop's name up to date with what De Lijn calls it now. */
    suspend fun refreshName(stop: StopKey, name: String?, municipality: String?) {
        if (name.isNullOrBlank()) return
        if (stops.value.none { it.key == stop && (it.name != name || (municipality != null && it.municipality != municipality)) }) return
        edit { list -> list.map { if (it.key == stop) it.copy(name = name, municipality = municipality ?: it.municipality) else it } }
    }
}
