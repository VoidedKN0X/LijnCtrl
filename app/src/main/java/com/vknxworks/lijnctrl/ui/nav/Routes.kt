package com.vknxworks.lijnctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object StopsRoute
@Serializable data object NearbyRoute
@Serializable data object SearchRoute

@Serializable data class StopRoute(val entity: String, val number: String, val name: String? = null)

@Serializable data object YouRoute
@Serializable data object SettingsRoute
@Serializable data object ApiKeyRoute
