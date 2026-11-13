package com.vknxworks.lijnctrl.data.towns

import com.vknxworks.lijnctrl.data.api.ApiMunicipality
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.Normalizer

/** A town or village to browse all stops of, with its main municipality for sub-municipalities. */
data class Town(val number: String, val name: String, val partOf: String?)

/**
 * The towns and villages De Lijn serves, fetched once per app start. Searching for a town lists every stop in
 * it, which finds stops whose own name doesn't mention the town.
 */
class TownRepository(private val api: DeLijnApi) {
    private val mutex = Mutex()
    private var towns: List<Town>? = null

    private suspend fun all(): List<Town> = mutex.withLock {
        towns ?: api.municipalities().mapNotNull(::toTown).sortedBy { it.name }.also { towns = it }
    }

    private fun toTown(m: ApiMunicipality): Town? {
        val name = m.name?.takeIf { it.isNotBlank() } ?: return null
        return Town(m.number, titleCase(name), m.main?.name?.takeIf { it.isNotBlank() && !it.equals(name, true) }?.let(::titleCase))
    }

    /** Towns whose name starts with [query] (accents and case ignored), best matches first. Never throws. */
    suspend fun matching(query: String, max: Int = 5): List<Town> {
        val q = fold(query.trim())
        if (q.length < 2) return emptyList()
        val list = runCatching { all() }.getOrDefault(emptyList())
        return list.filter { fold(it.name).startsWith(q) || fold(it.name).split(' ', '-').any { w -> w.startsWith(q) } }
            .sortedWith(compareBy<Town>({ fold(it.name) != q }, { it.partOf != null }, { it.name.length }))
            .take(max)
    }

    companion object {
        fun fold(s: String): String =
            Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

        /** De Lijn writes towns in capitals ("KNOKKE-HEIST"); show them as "Knokke-Heist". */
        fun titleCase(s: String): String =
            if (s != s.uppercase()) s
            else s.lowercase().split(' ').joinToString(" ") { word ->
                word.split('-').joinToString("-") { part -> part.replaceFirstChar { it.titlecase() } }
            }
    }
}
