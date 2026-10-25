package com.vknxworks.lijnctrl.ui.nav

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.Lifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import kotlin.reflect.KClass

/** One place that knows where things open. */
class Navigator(val nav: NavHostController) {

    /** True when the screen on top is fully shown (not still animating in or already on its way out). */
    private fun settled(): Boolean =
        nav.currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != false

    /** Opens a screen from a tap, ignoring taps on a screen that's already leaving or not shown yet. */
    fun navigate(route: Any) {
        if (!settled()) return
        nav.navigate(route) { launchSingleTop = true }
    }

    /**
     * Back from the screen the user is looking at. Repeat taps while a screen change is still
     * animating are ignored, and the first screen is never popped (that emptied the screen stack
     * and left a black screen until the app was restarted).
     */
    fun back() {
        val current = nav.currentBackStackEntry ?: return
        if (nav.previousBackStackEntry == null) return
        if (!current.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        nav.popBackStack()
    }

    /** Closes [route] if it's still on top, for screens that finish on their own (not from a tap). */
    fun close(route: KClass<out Any>) {
        val dest = nav.currentBackStackEntry?.destination ?: return
        if (nav.previousBackStackEntry != null && dest.hasRoute(route)) nav.popBackStack()
    }

    fun stop(entity: String, number: String, name: String? = null) {
        navigate(StopRoute(entity, number, name))
    }

    fun timetable(entity: String, number: String, name: String?) {
        navigate(TimetableRoute(entity, number, name))
    }

    fun line(entity: String, line: String, direction: String? = null) {
        navigate(LineRoute(entity, line, direction))
    }

    fun place(latitude: Double, longitude: Double, name: String, detail: String?) {
        navigate(PlaceRoute(latitude, longitude, name, detail))
    }

    /** Sets [point] as the destination on the Plan tab and opens it. */
    fun routeTo(point: com.vknxworks.lijnctrl.data.plan.PlanPoint) {
        com.vknxworks.lijnctrl.ui.plan.PlanState.routeTo(point)
        if (com.vknxworks.lijnctrl.ui.plan.PlanState.from == null) {
            com.vknxworks.lijnctrl.ui.plan.PlanState.from = com.vknxworks.lijnctrl.data.plan.PlanPoint(
                com.vknxworks.lijnctrl.str(com.vknxworks.lijnctrl.R.string.my_location), 0.0, 0.0, isMyLocation = true,
            )
        }
        if (!settled()) return
        nav.navigate(PlanRoute) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun town(number: String, name: String) {
        navigate(TownRoute(number, name))
    }

    /** Opens the trip a departure belongs to, when De Lijn said which trip it is. */
    fun trip(d: com.vknxworks.lijnctrl.data.api.Departure) {
        val line = d.line ?: return
        val trip = d.trip ?: return
        val direction = d.direction ?: return
        val date = d.serviceDate ?: return
        navigate(TripRoute(line.entity, line.line, direction, trip, date.toString(), d.stop))
    }
}

val LocalNavigator = staticCompositionLocalOf<Navigator> { error("No navigator") }
