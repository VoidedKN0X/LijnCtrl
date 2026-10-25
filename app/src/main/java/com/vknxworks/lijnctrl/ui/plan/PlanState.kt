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

    /** Set by a "Go home" / "Go to work" shortcut: the Plan tab plans as soon as it's shown. */
    var autoPlan by mutableStateOf(false)

    /** From where you are to [place], leaving now; with [plan], the Plan tab plans it straight away. */
    fun goTo(place: PlanPoint, plan: Boolean) {
        from = PlanPoint(com.vknxworks.lijnctrl.str(com.vknxworks.lijnctrl.R.string.my_location), 0.0, 0.0, isMyLocation = true)
        to = place
        mode = TimeMode.NOW
        time = null
        results = null
        error = null
        autoPlan = plan
    }

    /** Sets the destination (from a stop or place page) and clears old results. */
    fun routeTo(point: PlanPoint) {
        to = point
        results = null
        error = null
    }
}
