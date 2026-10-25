package com.vknxworks.lijnctrl.data.plan

import java.time.Instant

/**
 * Plans with the planner chosen in Settings: the one on the phone (De Lijn only, private) or Transitous
 * (every operator, live delays, but the route's ends go to transitous.org).
 */
class PlanRepository(private val builtIn: BuiltInPlanner, private val transitous: TransitousPlanner) {
    suspend fun plan(from: PlanPoint, to: PlanPoint, time: Instant, arriveBy: Boolean, useTransitous: Boolean): List<Journey> =
        if (useTransitous) transitous.plan(from, to, time, arriveBy) else builtIn.plan(from, to, time, arriveBy)
}
