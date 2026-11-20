package com.vknxworks.lijnctrl.data.favourites

import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.saved.SavedStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.serialization.Serializable

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

/** A line saved on the Stops tab, with what's needed to draw its badge without a lookup. */
@Serializable
data class SavedLine(
    val entity: String,
    val line: String,
    val publicNumber: String,
    val description: String? = null,
) {
    val key: com.vknxworks.lijnctrl.data.api.LineKey get() = com.vknxworks.lijnctrl.data.api.LineKey(entity, line)
}

/** Saved stops and lines, in the order you put them. Kept only on this phone, in the encrypted saved file ([SavedStore]). */
class FavouritesRepository(private val store: SavedStore, scope: CoroutineScope) {
    val stops: StateFlow<List<SavedStop>> = store.data
        .map { it.stops }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val lines: StateFlow<List<SavedLine>> = store.data
        .map { it.lines }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun toggleLine(line: SavedLine) {
        store.update { d ->
            val current = d.lines
            d.copy(lines = if (current.any { it.key == line.key }) current.filterNot { it.key == line.key } else current + line)
        }
    }

    // Line filters: per stop, the lines you want to see there (none saved = every line).

    /** Stop (as "entity_number") to the lines shown there (as "entity_line"). */
    val filters: StateFlow<Map<String, Set<String>>> = store.data
        .map { d -> d.filters.mapValues { it.value.toSet() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Shows only [lines] at [stop]; an empty set shows every line again. */
    suspend fun setFilter(stop: StopKey, lines: Set<String>) {
        store.update { d ->
            val next = d.filters.toMutableMap()
            if (lines.isEmpty()) next.remove(stop.toString()) else next[stop.toString()] = lines.sorted()
            d.copy(filters = next)
        }
    }

    fun isSaved(stop: StopKey, list: List<SavedStop> = stops.value) = list.any { it.key == stop }

    private suspend fun edit(transform: (List<SavedStop>) -> List<SavedStop>) {
        store.update { d -> d.copy(stops = transform(d.stops)) }
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
