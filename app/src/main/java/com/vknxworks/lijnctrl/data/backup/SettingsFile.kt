package com.vknxworks.lijnctrl.data.backup

import com.vknxworks.lijnctrl.data.favourites.SavedLine
import com.vknxworks.lijnctrl.data.favourites.SavedStop
import com.vknxworks.lijnctrl.data.plan.SavedPoint
import com.vknxworks.lijnctrl.data.saved.SavedData
import com.vknxworks.lijnctrl.data.settings.AppSettings
import com.vknxworks.lijnctrl.data.settings.MessageLanguage
import com.vknxworks.lijnctrl.data.settings.NearbyRadius
import com.vknxworks.lijnctrl.data.settings.ThemeChoice
import com.vknxworks.lijnctrl.data.settings.TimeStyle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The settings part of an export: only plain preferences, by name, so a newer or older app can read them. */
@Serializable
data class ExportedSettings(
    val theme: String? = null,
    val timeStyle: String? = null,
    val messageLanguage: String? = null,
    val nearbyRadius: String? = null,
    val useTransitous: Boolean? = null,
    val planHistory: Boolean? = null,
)

/**
 * An export of saved stops and settings. It never holds the De Lijn or GTFS keys, the app lock PIN, or the
 * security switches (app lock timing, hide in screenshots or recent apps); recent routes and which disruptions
 * you were told about stay on the phone too.
 */
@Serializable
data class SettingsExport(
    val app: String = APP,
    val format: Int = FORMAT,
    val created: String? = null,
    val appVersion: String? = null,
    val stops: List<SavedStop> = emptyList(),
    val lines: List<SavedLine> = emptyList(),
    val filters: Map<String, List<String>> = emptyMap(),
    val home: SavedPoint? = null,
    val work: SavedPoint? = null,
    val settings: ExportedSettings = ExportedSettings(),
) {
    companion object {
        const val APP = "LijnCtrl"
        const val FORMAT = 1
    }
}

class ExportFormatException(val newer: Boolean) : Exception()

object SettingsFile {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    /** Biggest file accepted: a settings export is a few kilobytes. */
    const val MAX_BYTES = 512 * 1024

    fun export(saved: SavedData, settings: AppSettings, appVersion: String, created: String): String = json.encodeToString(
        SettingsExport.serializer(),
        SettingsExport(
            created = created,
            appVersion = appVersion,
            stops = saved.stops,
            lines = saved.lines,
            filters = saved.filters,
            home = saved.home,
            work = saved.work,
            settings = ExportedSettings(
                theme = settings.theme.name,
                timeStyle = settings.timeStyle.name,
                // Left out while it still follows the phone, so importing on another phone does the same there.
                messageLanguage = settings.messageLanguage.name.takeIf { settings.messageLanguageChosen },
                nearbyRadius = settings.nearbyRadius.name,
                useTransitous = settings.useTransitous,
                planHistory = settings.planHistory,
            ),
        ),
    )

    /** Reads and checks an export. Nothing on the phone changes here. */
    fun parse(text: String): SettingsExport {
        val e = try {
            json.decodeFromString(SettingsExport.serializer(), text)
        } catch (x: Exception) {
            throw ExportFormatException(newer = false)
        }
        if (e.app != SettingsExport.APP) throw ExportFormatException(newer = false)
        if (e.format > SettingsExport.FORMAT) throw ExportFormatException(newer = true)
        // Stop and line numbers are digits (entity 1-5); anything else isn't from LijnCtrl.
        val okStops = e.stops.all { it.entity.matches(DIGITS) && it.number.matches(DIGITS) && it.name.length <= 200 }
        val okLines = e.lines.all { it.entity.matches(DIGITS) && it.line.matches(DIGITS) && it.publicNumber.length <= 20 }
        if (!okStops || !okLines || e.stops.size > 500 || e.lines.size > 500) throw ExportFormatException(newer = false)
        return e
    }

    /** The saved data with the export's stops, lines, filters and Home/Work; recent routes and seen disruptions stay. */
    fun applyTo(saved: SavedData, e: SettingsExport): SavedData = saved.copy(
        stops = e.stops.distinctBy { it.entity to it.number },
        lines = e.lines.distinctBy { it.entity to it.line },
        filters = e.filters.filterKeys { it.length <= 40 }.mapValues { (_, v) -> v.filter { it.length <= 40 }.distinct().sorted() },
        home = e.home?.takeIf { it.isPlausible() },
        work = e.work?.takeIf { it.isPlausible() },
    )

    /** The settings with the export's preferences; anything missing or unknown keeps its current value. */
    fun applyTo(s: AppSettings, e: ExportedSettings): AppSettings {
        val language = MessageLanguage.entries.firstOrNull { it.name == e.messageLanguage }
        return applyTo(s, e, language)
    }

    private fun applyTo(s: AppSettings, e: ExportedSettings, language: MessageLanguage?): AppSettings = s.copy(
        theme = ThemeChoice.entries.firstOrNull { it.name == e.theme } ?: s.theme,
        timeStyle = TimeStyle.entries.firstOrNull { it.name == e.timeStyle } ?: s.timeStyle,
        messageLanguage = language ?: s.messageLanguage,
        messageLanguageChosen = language != null || s.messageLanguageChosen,
        nearbyRadius = NearbyRadius.entries.firstOrNull { it.name == e.nearbyRadius } ?: s.nearbyRadius,
        useTransitous = e.useTransitous ?: s.useTransitous,
        planHistory = e.planHistory ?: s.planHistory,
    )

    private fun SavedPoint.isPlausible(): Boolean =
        !isMyLocation && name.isNotBlank() && latitude in -90.0..90.0 && longitude in -180.0..180.0

    private val DIGITS = Regex("\\d{1,12}")
}
