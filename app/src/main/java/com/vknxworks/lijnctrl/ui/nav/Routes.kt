package com.vknxworks.lijnctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object StopsRoute
@Serializable data object NearbyRoute
@Serializable data object SearchRoute

@Serializable data class StopRoute(val entity: String, val number: String, val name: String? = null)
@Serializable data class TimetableRoute(val entity: String, val number: String, val name: String? = null)
@Serializable data class LineRoute(val entity: String, val line: String, val direction: String? = null)
@Serializable data class TripRoute(
    val entity: String,
    val line: String,
    val direction: String,
    val trip: String,
    /** The service day, yyyy-MM-dd. */
    val date: String,
    /** The stop you opened the trip from, highlighted on the page. */
    val fromStop: String? = null,
)
@Serializable data class TownRoute(val number: String, val name: String)

@Serializable data object YouRoute
@Serializable data object SettingsRoute
@Serializable data object ApiKeyRoute
