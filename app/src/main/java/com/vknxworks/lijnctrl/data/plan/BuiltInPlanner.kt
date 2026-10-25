package com.vknxworks.lijnctrl.data.plan

import com.vknxworks.lijnctrl.data.api.ApiStop
import com.vknxworks.lijnctrl.data.api.BRUSSELS
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.api.Departure
import com.vknxworks.lijnctrl.data.api.LineKey
import com.vknxworks.lijnctrl.data.api.StopKey
import com.vknxworks.lijnctrl.data.departures.DepartureRepository
import com.vknxworks.lijnctrl.data.lines.LineRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * A route planner that runs on the phone, using only De Lijn's open data. It finds the stops within walking
 * distance of both ends, the lines that connect them directly or with one change, and times them with the stops'
 * timetables. It only knows De Lijn buses and trams, so it can't suggest trains, and it plans with the timetable
 * (the trip page then shows live times).
 */
class BuiltInPlanner(
    private val api: DeLijnApi,
    private val departures: DepartureRepository,
    private val lines: LineRepository,
) {
    /** A stop you can walk to (or from), and how long the walk takes. */
    private data class Access(val stop: StopKey, val name: String, val lat: Double, val lon: Double, val walk: Long)

    /** One direction of one line. */
    private data class Dir(val line: LineKey, val direction: String) {
        override fun toString() = "${line}_$direction"
    }

    private val mutex = Mutex()
    private val routeCache = mutableMapOf<String, List<ApiStop>>()
    private val stopLineCache = mutableMapOf<StopKey, List<Dir>>()

    /** De Lijn is asked at most this many things at the same time. */
    private val gate = Semaphore(6)

    private suspend fun <T> limited(block: suspend () -> T): T = gate.withPermit { block() }

    private suspend fun route(d: Dir): List<ApiStop> {
        mutex.withLock { routeCache[d.toString()] }?.let { return it }
        val stops = runCatching { limited { api.lineStops(d.line, d.direction) } }.getOrDefault(emptyList())
        mutex.withLock { routeCache[d.toString()] = stops }
        return stops
    }

    private suspend fun linesAt(stop: StopKey): List<Dir> {
        mutex.withLock { stopLineCache[stop] }?.let { return it }
        val dirs = runCatching { limited { api.stopLines(stop) } }.getOrDefault(emptyList())
            .mapNotNull { d -> d.direction?.let { Dir(LineKey(d.entity, d.line), it) } }.distinct()
        mutex.withLock { stopLineCache[stop] = dirs }
        return dirs
    }

    /** Stops within walking distance of [p] (or the stop itself, plus the ones right next to it). */
    private suspend fun access(p: PlanPoint): List<Access> {
        val radius = if (p.stop != null) 250 else 800
        val nearby = runCatching { limited { api.stopsNearby(p.latitude, p.longitude, max = 20, radius = radius) } }
            .getOrNull()?.stops.orEmpty()
            .filter { it.type == null || it.type.equals("DELIJN", true) }
            .mapNotNull { s ->
                val c = s.coordinate ?: return@mapNotNull null
                val entity = DeLijnApi.entityFromLinks(s) ?: s.id.take(1)
                val metres = s.distance?.toDouble() ?: distanceMetres(p.latitude, p.longitude, c.latitude, c.longitude)
                Access(StopKey(entity, s.id), s.name ?: s.id, c.latitude, c.longitude, walkSeconds(metres))
            }
            .sortedBy { it.walk }
        val own = p.stop?.let { listOf(Access(it, p.name, p.latitude, p.longitude, 0)) }.orEmpty()
        return (own + nearby).distinctBy { it.stop }.take(if (p.stop != null) 4 else 8)
    }

    /** Timetabled departures at [stop] from [from] on: that day's, plus the next day's late in the evening. */
    private suspend fun timetableFrom(stop: StopKey, from: Instant): List<Departure> {
        val day = from.atZone(BRUSSELS).toLocalDate()
        val time = from.atZone(BRUSSELS).toLocalTime()
        val days = buildList {
            if (time.isBefore(LocalTime.of(4, 0))) add(day.minusDays(1))
            add(day)
            if (time.isAfter(LocalTime.of(20, 0))) add(day.plusDays(1))
        }
        return days.flatMap { d -> runCatching { limited { departures.timetable(stop, d) } }.getOrDefault(emptyList()) }
            .filter { !it.scheduled.isBefore(from) }
            .sortedBy { it.scheduled }
    }

    /** When the trip [dep] (line, direction, trip, day) gets to [stop], from that stop's timetable. */
    private suspend fun arrivalAt(stop: StopKey, dep: Departure): Departure? {
        val date = dep.serviceDate ?: return null
        val list = runCatching { limited { departures.timetable(stop, date) } }.getOrDefault(emptyList())
        return list.firstOrNull { it.line == dep.line && it.direction == dep.direction && it.trip == dep.trip && it.scheduled.isAfter(dep.scheduled) }
    }

    private fun Departure.matches(d: Dir) = line == d.line && direction == d.direction

    suspend fun plan(from: PlanPoint, to: PlanPoint, time: Instant, arriveBy: Boolean): List<Journey> = coroutineScope {
        // Arrive by: search from a while before, then keep the routes that make it in time, latest first.
        val searchFrom = if (arriveBy) time.minusSeconds(150 * 60) else time
        val origins = async { access(from) }
        val dests = async { access(to) }
        val o = origins.await()
        val d = dests.await()
        if (o.isEmpty() || d.isEmpty()) return@coroutineScope emptyList()

        val oLines = o.map { a -> async { a to linesAt(a.stop) } }.awaitAll()
        val dLines = d.map { a -> async { a to linesAt(a.stop) } }.awaitAll()
        val oDirs = oLines.flatMap { (a, dirs) -> dirs.map { it to a } }.groupBy({ it.first }, { it.second })
        val dDirs = dLines.flatMap { (a, dirs) -> dirs.map { it to a } }.groupBy({ it.first }, { it.second })

        val journeys = mutableListOf<Journey>()
        val perOption = if (arriveBy) 6 else 3

        // ---- Direct: one line that calls at a stop near the start and, later, at a stop near the end.
        val direct = oDirs.keys.intersect(dDirs.keys)
        val directJobs = direct.map { dir ->
            async {
                val stops = route(dir)
                val found = mutableListOf<Journey>()
                for (oa in oDirs[dir].orEmpty()) for (da in dDirs[dir].orEmpty()) {
                    val io = stops.indexOfFirst { it.number == oa.stop.number }
                    val id = stops.indexOfLast { it.number == da.stop.number }
                    if (io < 0 || id <= io) continue
                    val deps = timetableFrom(oa.stop, searchFrom.plusSeconds(oa.walk)).filter { it.matches(dir) }.take(perOption)
                    for (dep in deps) {
                        val arr = arrivalAt(da.stop, dep) ?: continue
                        found += build(from, to, oa, da, listOf(ride(dep, arr, oa, da, id - io - 1)))
                    }
                }
                found
            }
        }
        journeys += directJobs.awaitAll().flatten()

        // ---- One change: line A from near the start to a stop where line B, to near the end, calls.
        if (journeys.distinctBy { it.signature }.size < 4) {
            val firstLegs = oDirs.keys.filter { it !in direct }.take(10)
            val secondLegs = dDirs.keys.filter { it !in direct }.take(10)
            val routes = (firstLegs + secondLegs).distinct().map { dir -> async { dir to route(dir) } }.awaitAll().toMap()
            data class Change(val a: Dir, val b: Dir, val oa: Access, val da: Access, val t1: ApiStop, val t2: ApiStop, val n1: Int, val n2: Int)
            val changes = mutableListOf<Change>()
            for (a in firstLegs) for (b in secondLegs) {
                if (a.line == b.line) continue
                val ra = routes[a].orEmpty()
                val rb = routes[b].orEmpty()
                val oa = oDirs[a].orEmpty().minByOrNull { it.walk } ?: continue
                val da = dDirs[b].orEmpty().minByOrNull { it.walk } ?: continue
                val io = ra.indexOfFirst { it.number == oa.stop.number }
                val id = rb.indexOfLast { it.number == da.stop.number }
                if (io < 0 || id < 0) continue
                // The first stop along line A where line B can be caught, at the same stop or one close by.
                var picked: Change? = null
                loop@ for (i in io + 1 until ra.size) {
                    val t1 = ra[i]
                    val c1 = t1.coordinate
                    for (j in 0 until id) {
                        val t2 = rb[j]
                        val c2 = t2.coordinate
                        val same = t1.number == t2.number
                        val close = c1 != null && c2 != null && c1.latitude != 0.0 && c2.latitude != 0.0 &&
                            distanceMetres(c1.latitude, c1.longitude, c2.latitude, c2.longitude) <= 250
                        if (same || close) {
                            picked = Change(a, b, oa, da, t1, t2, i - io - 1, id - j - 1)
                            break@loop
                        }
                    }
                }
                picked?.let { changes += it }
            }
            val changeJobs = changes.sortedBy { it.n1 + it.n2 }.take(8).map { ch ->
                async {
                    val found = mutableListOf<Journey>()
                    val t1Key = StopKey(ch.t1.entity, ch.t1.number)
                    val t2Key = StopKey(ch.t2.entity, ch.t2.number)
                    val transferWalk = if (t1Key == t2Key) 0L else {
                        val c1 = ch.t1.coordinate
                        val c2 = ch.t2.coordinate
                        if (c1 != null && c2 != null) walkSeconds(distanceMetres(c1.latitude, c1.longitude, c2.latitude, c2.longitude)) else 120L
                    }
                    val firstDeps = timetableFrom(ch.oa.stop, searchFrom.plusSeconds(ch.oa.walk)).filter { it.matches(ch.a) }.take(perOption)
                    for (dep in firstDeps) {
                        val arr1 = arrivalAt(t1Key, dep) ?: continue
                        // At least two minutes to change, plus the walk between the stops.
                        val ready = arr1.scheduled.plusSeconds(120 + transferWalk)
                        val dep2 = timetableFrom(t2Key, ready).firstOrNull { it.matches(ch.b) } ?: continue
                        val arr2 = arrivalAt(ch.da.stop, dep2) ?: continue
                        val oa = ch.oa
                        val da = ch.da
                        val legs = buildList {
                            add(ride(dep, arr1, oa, Access(t1Key, ch.t1.name ?: ch.t1.number, 0.0, 0.0, 0), ch.n1))
                            if (transferWalk > 0) {
                                add(walk(ch.t1.name ?: ch.t1.number, ch.t2.name ?: ch.t2.number, arr1.scheduled, transferWalk))
                            }
                            add(ride(dep2, arr2, Access(t2Key, ch.t2.name ?: ch.t2.number, 0.0, 0.0, 0), da, ch.n2))
                        }
                        found += build(from, to, oa, da, legs)
                    }
                    found
                }
            }
            journeys += changeJobs.awaitAll().flatten()
        }

        // Badges for every line used, so they can be drawn in De Lijn's colours.
        runCatching { lines.ensure(journeys.flatMap { j -> j.legs.mapNotNull { it.line } }.toSet()) }

        val unique = journeys.distinctBy { it.signature }
        if (arriveBy) unique.filter { !it.end.isAfter(time) }.sortedByDescending { it.start }.take(6).sortedBy { it.start }
        else unique.sortedWith(compareBy<Journey>({ it.end }, { it.transfers })).take(6)
    }

    private fun ride(dep: Departure, arr: Departure, from: Access, to: Access, between: Int): JourneyLeg {
        val info = dep.line?.let { lines.get(it) }
        return JourneyLeg(
            mode = if (info?.isTram == true) LegMode.TRAM else LegMode.BUS,
            fromName = from.name,
            toName = to.name,
            start = dep.scheduled,
            end = arr.scheduled,
            scheduledStart = dep.scheduled,
            scheduledEnd = arr.scheduled,
            badge = info?.publicNumber ?: dep.line?.line,
            badgeBackground = info?.background,
            badgeForeground = info?.foreground,
            headsign = dep.destination,
            operator = "De Lijn",
            stopsBetween = between.coerceAtLeast(0),
            line = dep.line,
            direction = dep.direction,
            trip = dep.trip,
            serviceDate = dep.serviceDate,
            fromStop = from.stop,
            toStop = to.stop,
        )
    }

    private fun walk(fromName: String, toName: String, start: Instant, seconds: Long): JourneyLeg =
        JourneyLeg(LegMode.WALK, fromName, toName, start, start.plusSeconds(seconds), distanceMetres = (seconds * 1.3 / 1.25).toInt())

    /** Adds the walks at both ends (when there's anything to walk) around the rides. */
    private fun build(from: PlanPoint, to: PlanPoint, oa: Access, da: Access, rides: List<JourneyLeg>): Journey {
        val legs = buildList {
            if (oa.walk >= 30) add(walk(from.name, oa.name, rides.first().start.minusSeconds(oa.walk), oa.walk))
            addAll(rides)
            if (da.walk >= 30) add(walk(da.name, to.name, rides.last().end, da.walk))
        }
        return Journey(legs, SOURCE)
    }

    companion object {
        const val SOURCE = "builtin"
    }
}

/** Today's date in Belgium, for planners and pickers. */
fun belgianToday(): LocalDate = LocalDate.now(BRUSSELS)
