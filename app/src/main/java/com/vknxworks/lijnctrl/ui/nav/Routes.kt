package com.vknxworks.lijnctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object StopsRoute
@Serializable data object NearbyRoute
@Serializable data object SearchRoute
@Serializable data object PlanRoute

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
/** A place from the search (an address, a shop, a landmark): the stops around it. */
@Serializable data class PlaceRoute(val latitude: Double, val longitude: Double, val name: String, val detail: String? = null)

@Serializable data object YouRoute
@Serializable data object SettingsRoute
@Serializable data object ApiKeyRoute
/** Picks the start or end of a route; with [saveAs] ("HOME" or "WORK") it saves that place instead. */
@Serializable data class PickPlaceRoute(val forFrom: Boolean, val saveAs: String? = null)
@Serializable data class JourneyRoute(val index: Int)
@Serializable data class RouteMapRoute(val index: Int)
@Serializable data object GtfsKeyRoute
