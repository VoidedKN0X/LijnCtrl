package com.vknxworks.lijnctrl.data.places

import com.vknxworks.lijnctrl.data.api.StopKey

enum class PlaceKind { PLACE, ADDRESS, STREET, STOP }

/** A search result for an address, a street, a shop or landmark, or a stop. */
data class PlaceResult(
    val name: String,
    /** Street and town, shown under the name. */
    val detail: String?,
    val latitude: Double,
    val longitude: Double,
    val kind: PlaceKind,
    /** Set for De Lijn stops, which open the stop itself. */
    val stop: StopKey? = null,
)
