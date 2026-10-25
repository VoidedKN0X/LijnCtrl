package com.vknxworks.lijnctrl.ui.plan

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vknxworks.lijnctrl.data.plan.Journey
import com.vknxworks.lijnctrl.data.plan.PlanPoint
import java.time.Instant

enum class TimeMode { NOW, DEPART_AT, ARRIVE_BY }

/**
 * What's on the Plan tab: kept while the app runs, so picking a place, opening a journey or a stop and coming
 * back doesn't lose it. Never saved to the phone.
 */
object PlanState {
    var from by mutableStateOf<PlanPoint?>(null)
    var to by mutableStateOf<PlanPoint?>(null)
    var mode by mutableStateOf(TimeMode.NOW)
    var time by mutableStateOf<Instant?>(null)
    var results by mutableStateOf<List<Journey>?>(null)
    var error by mutableStateOf<String?>(null)
    var planning by mutableStateOf(false)

    /** Sets the destination (from a stop or place page) and clears old results. */
    fun routeTo(point: PlanPoint) {
        to = point
        results = null
        error = null
    }
}
