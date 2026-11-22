package com.vknxworks.lijnctrl.data.live

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** One stop a live trip is still heading to, with its expected time (epoch seconds) when known. */
data class RtStopTime(val stopId: String, val sequence: Int?, val time: Long?)

/** A trip on the road: its id, the vehicle running it, and its expected times at the stops still ahead. */
data class RtTrip(val tripId: String, val startDate: String?, val vehicleId: String?, val stops: List<RtStopTime>)

/** Where a vehicle is. Bearing in degrees from north, speed in m/s; timestamp in epoch seconds. */
data class RtVehicle(
    val vehicleId: String?,
    val tripId: String?,
    val latitude: Double,
    val longitude: Double,
    val bearing: Float?,
    val speed: Float?,
    val timestamp: Long?,
    /** GTFS Realtime OccupancyStatus (0 EMPTY … 8 NOT_BOARDABLE), when the feed gives one. */
    val occupancy: Int? = null,
)

/** A text in one language (null: not said). */
data class RtText(val language: String?, val text: String)

/**
 * A service alert from the feed: what it says (in every language given), what it's about (stops, routes, trips)
 * and when it applies (epoch seconds; null ends are open).
 */
data class RtAlert(
    val id: String?,
    val header: List<RtText>,
    val description: List<RtText>,
    val stops: Set<String>,
    val routes: Set<String>,
    val trips: Set<String>,
    val periods: List<Pair<Long?, Long?>>,
    /** GTFS Realtime Effect (1 NO_SERVICE … 11 ACCESSIBILITY_ISSUE), when given. */
    val effect: Int?,
) {
    fun activeAt(epochSecond: Long): Boolean =
        periods.isEmpty() || periods.any { (a, b) -> (a == null || a <= epochSecond) && (b == null || epochSecond < b) }
}

data class RtFeed(val timestamp: Long?, val trips: List<RtTrip>, val vehicles: List<RtVehicle>, val alerts: List<RtAlert> = emptyList()) {
    /** Trips and vehicles (alerts not counted: a one-vehicle answer can still carry every alert). */
    val entityCount: Int get() = trips.size + vehicles.size
}

/**
 * Reads De Lijn's GTFS Realtime feed: the compact protobuf format (the default, much smaller), or JSON when
 * that's what comes back. Only the fields LijnCtrl uses are read; everything else is skipped.
 */
object GtfsRealtime {
    fun parse(raw: ByteArray): RtFeed {
        // De Lijn sends the feed gzipped, sometimes without saying so in the headers: unpack it here then.
        val bytes = if (raw.size > 2 && raw[0] == 0x1f.toByte() && raw[1] == 0x8b.toByte()) {
            // Unpacked with a limit, so a tiny "zip bomb" can't fill the memory.
            val limit = com.vknxworks.lijnctrl.data.api.MAX_FEED_UNPACKED_BYTES.toInt()
            java.util.zip.GZIPInputStream(raw.inputStream()).use { it.readNBytes(limit + 1) }
                .also { if (it.size > limit) throw IllegalArgumentException("feed too large") }
        } else raw
        val firstNonSpace = bytes.firstOrNull { it.toInt().toChar() !in " \n\r\t" }
        return if (firstNonSpace?.toInt()?.toChar() == '{') parseJson(bytes.decodeToString()) else parseProto(bytes)
    }

    // ---------- protobuf ----------

    private class Reader(val buf: ByteArray, var pos: Int, val end: Int) {
        fun more() = pos < end
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (true) {
                if (pos >= end || shift > 63) throw IllegalArgumentException("bad varint")
                val b = buf[pos++].toInt()
                result = result or ((b and 0x7f).toLong() shl shift)
                if (b and 0x80 == 0) return result
                shift += 7
            }
        }
        fun fixed32(): Int {
            if (pos + 4 > end) throw IllegalArgumentException("short")
            val v = (buf[pos].toInt() and 0xff) or ((buf[pos + 1].toInt() and 0xff) shl 8) or
                ((buf[pos + 2].toInt() and 0xff) shl 16) or ((buf[pos + 3].toInt() and 0xff) shl 24)
            pos += 4
            return v
        }
        fun fixed64(): Long {
            val lo = fixed32().toLong() and 0xffffffffL
            val hi = fixed32().toLong() and 0xffffffffL
            return lo or (hi shl 32)
        }
        fun sub(): Reader {
            // Checked as a Long against what's left, so a huge or negative length can't overflow past the check.
            val l = varint()
            if (l < 0 || l > (end - pos).toLong()) throw IllegalArgumentException("bad length")
            val len = l.toInt()
            val r = Reader(buf, pos, pos + len)
            pos += len
            return r
        }
        fun string(): String {
            val r = sub()
            return String(buf, r.pos, r.end - r.pos, Charsets.UTF_8)
        }
        fun skip(wire: Int) {
            when (wire) {
                0 -> varint()
                1 -> pos += 8
                2 -> sub()
                5 -> pos += 4
                else -> throw IllegalArgumentException("wire type $wire")
            }
        }
        inline fun fields(block: (field: Int, wire: Int) -> Unit) {
            while (more()) {
                val tag = varint()
                block((tag ushr 3).toInt(), (tag and 7).toInt())
            }
        }
    }

    private fun parseProto(bytes: ByteArray): RtFeed {
        val r = Reader(bytes, 0, bytes.size)
        var timestamp: Long? = null
        val trips = mutableListOf<RtTrip>()
        val vehicles = mutableListOf<RtVehicle>()
        val alerts = mutableListOf<RtAlert>()
        r.fields { f, w ->
            when {
                f == 1 && w == 2 -> r.sub().let { h -> h.fields { hf, hw -> if (hf == 3 && hw == 0) timestamp = h.varint() else h.skip(hw) } }
                f == 2 && w == 2 -> entity(r.sub(), trips, vehicles, alerts)
                else -> r.skip(w)
            }
        }
        return RtFeed(timestamp, trips, vehicles, alerts)
    }

    /** FeedEntity: id (1), trip_update (3), vehicle (4), alert (5). */
    private fun entity(r: Reader, trips: MutableList<RtTrip>, vehicles: MutableList<RtVehicle>, alerts: MutableList<RtAlert>) {
        var id: String? = null
        var alert: Reader? = null
        r.fields { f, w ->
            when {
                f == 1 && w == 2 -> id = r.string()
                f == 3 && w == 2 -> tripUpdate(r.sub())?.let(trips::add)
                f == 4 && w == 2 -> vehiclePosition(r.sub())?.let(vehicles::add)
                f == 5 && w == 2 -> alert = r.sub()
                else -> r.skip(w)
            }
        }
        alert?.let { a -> alert(a, id)?.let(alerts::add) }
    }

    /** TranslatedString: translation (1) = { text (1), language (2) }. */
    private fun translated(r: Reader): List<RtText> {
        val out = mutableListOf<RtText>()
        r.fields { f, w ->
            if (f == 1 && w == 2) {
                val t = r.sub()
                var text: String? = null
                var lang: String? = null
                t.fields { tf, tw ->
                    when {
                        tf == 1 && tw == 2 -> text = t.string()
                        tf == 2 && tw == 2 -> lang = t.string()
                        else -> t.skip(tw)
                    }
                }
                text?.takeIf { it.isNotBlank() }?.let { out += RtText(lang, it) }
            } else {
                r.skip(w)
            }
        }
        return out
    }

    /**
     * Alert: active_period (1) and impact_period (3) as TimeRange { start (1), end (2) }, informed_entity (5) as
     * EntitySelector { route_id (2), trip (4), stop_id (5) }, effect (7), header_text (10), description_text (11).
     */
    private fun alert(r: Reader, id: String?): RtAlert? {
        val header = mutableListOf<RtText>()
        val description = mutableListOf<RtText>()
        val stops = mutableSetOf<String>()
        val routes = mutableSetOf<String>()
        val trips = mutableSetOf<String>()
        val periods = mutableListOf<Pair<Long?, Long?>>()
        var effect: Int? = null
        r.fields { f, w ->
            when {
                (f == 1 || f == 3) && w == 2 -> {
                    val t = r.sub()
                    var start: Long? = null
                    var end: Long? = null
                    t.fields { tf, tw ->
                        when {
                            tf == 1 && tw == 0 -> start = t.varint()
                            tf == 2 && tw == 0 -> end = t.varint()
                            else -> t.skip(tw)
                        }
                    }
                    periods += start to end
                }
                f == 5 && w == 2 -> {
                    val e = r.sub()
                    e.fields { ef, ew ->
                        when {
                            ef == 2 && ew == 2 -> routes += e.string()
                            ef == 4 && ew == 2 -> tripDescriptor(e.sub()).first?.let(trips::add)
                            ef == 5 && ew == 2 -> stops += e.string()
                            else -> e.skip(ew)
                        }
                    }
                }
                f == 7 && w == 0 -> effect = r.varint().toInt()
                f == 10 && w == 2 -> header += translated(r.sub())
                f == 11 && w == 2 -> description += translated(r.sub())
                else -> r.skip(w)
            }
        }
        if (header.isEmpty() && description.isEmpty()) return null
        return RtAlert(id, header, description, stops, routes, trips, periods, effect)
    }

    /** TripDescriptor: trip_id (1), start_date (3). */
    private fun tripDescriptor(r: Reader): Pair<String?, String?> {
        var id: String? = null
        var date: String? = null
        r.fields { f, w ->
            when {
                f == 1 && w == 2 -> id = r.string()
                f == 3 && w == 2 -> date = r.string()
                else -> r.skip(w)
            }
        }
        return id to date
    }

    /** VehicleDescriptor: id (1). */
    private fun vehicleId(r: Reader): String? {
        var id: String? = null
        r.fields { f, w -> if (f == 1 && w == 2) id = r.string() else r.skip(w) }
        return id
    }

    /** StopTimeEvent: time (2). */
    private fun eventTime(r: Reader): Long? {
        var t: Long? = null
        r.fields { f, w -> if (f == 2 && w == 0) t = r.varint() else r.skip(w) }
        return t
    }

    private fun tripUpdate(r: Reader): RtTrip? {
        var trip: Pair<String?, String?> = null to null
        var vehicle: String? = null
        val stops = mutableListOf<RtStopTime>()
        r.fields { f, w ->
            when {
                f == 1 && w == 2 -> trip = tripDescriptor(r.sub())
                f == 2 && w == 2 -> {
                    val s = r.sub()
                    var seq: Int? = null
                    var stop: String? = null
                    var arrival: Long? = null
                    var departure: Long? = null
                    s.fields { sf, sw ->
                        when {
                            sf == 1 && sw == 0 -> seq = s.varint().toInt()
                            sf == 2 && sw == 2 -> arrival = eventTime(s.sub())
                            sf == 3 && sw == 2 -> departure = eventTime(s.sub())
                            sf == 4 && sw == 2 -> stop = s.string()
                            else -> s.skip(sw)
                        }
                    }
                    stop?.let { stops += RtStopTime(it, seq, departure ?: arrival) }
                }
                f == 3 && w == 2 -> vehicle = vehicleId(r.sub())
                else -> r.skip(w)
            }
        }
        val id = trip.first ?: return null
        return RtTrip(id, trip.second, vehicle, stops)
    }

    private fun vehiclePosition(r: Reader): RtVehicle? {
        var tripId: String? = null
        var vehicle: String? = null
        var lat: Double? = null
        var lon: Double? = null
        var bearing: Float? = null
        var speed: Float? = null
        var timestamp: Long? = null
        var occupancy: Int? = null
        r.fields { f, w ->
            when {
                f == 9 && w == 0 -> occupancy = r.varint().toInt()
                f == 1 && w == 2 -> tripId = tripDescriptor(r.sub()).first
                f == 2 && w == 2 -> {
                    val p = r.sub()
                    p.fields { pf, pw ->
                        when {
                            pf == 1 && pw == 5 -> lat = Float.fromBits(p.fixed32()).toDouble()
                            pf == 2 && pw == 5 -> lon = Float.fromBits(p.fixed32()).toDouble()
                            pf == 3 && pw == 5 -> bearing = Float.fromBits(p.fixed32())
                            pf == 5 && pw == 5 -> speed = Float.fromBits(p.fixed32())
                            pf == 4 && pw == 1 -> p.fixed64()
                            else -> p.skip(pw)
                        }
                    }
                }
                f == 5 && w == 0 -> timestamp = r.varint()
                f == 8 && w == 2 -> vehicle = vehicleId(r.sub())
                else -> r.skip(w)
            }
        }
        val la = lat ?: return null
        val lo = lon ?: return null
        if (la == 0.0 && lo == 0.0) return null
        return RtVehicle(vehicle, tripId, la, lo, bearing, speed, timestamp, occupancy)
    }

    // ---------- JSON (same structure, camelCase names) ----------

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private fun JsonElement?.obj() = this as? JsonObject
    private fun JsonElement?.arr() = (this as? JsonArray).orEmpty()
    private fun JsonElement?.str() = (this as? JsonPrimitive)?.content
    private fun JsonElement?.long() = (this as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toLongOrNull() }
    private fun JsonElement?.double() = (this as? JsonPrimitive)?.let { it.doubleOrNull ?: it.content.toDoubleOrNull() }

    private fun parseJson(text: String): RtFeed {
        val root = json.parseToJsonElement(text).obj() ?: return RtFeed(null, emptyList(), emptyList())
        val trips = mutableListOf<RtTrip>()
        val vehicles = mutableListOf<RtVehicle>()
        val alerts = mutableListOf<RtAlert>()
        root["entity"].arr().forEach { e ->
            val o = e.obj() ?: return@forEach
            o["tripUpdate"].obj()?.let { tu ->
                val id = tu["trip"].obj()?.get("tripId").str() ?: return@let
                val stops = tu["stopTimeUpdate"].arr().mapNotNull { s ->
                    val so = s.obj() ?: return@mapNotNull null
                    val stop = so["stopId"].str() ?: return@mapNotNull null
                    val t = so["departure"].obj()?.get("time").long() ?: so["arrival"].obj()?.get("time").long()
                    RtStopTime(stop, so["stopSequence"].long()?.toInt(), t)
                }
                trips += RtTrip(id, tu["trip"].obj()?.get("startDate").str(), tu["vehicle"].obj()?.get("id").str(), stops)
            }
            o["vehicle"].obj()?.let { v ->
                val p = v["position"].obj() ?: return@let
                val la = p["latitude"].double() ?: return@let
                val lo = p["longitude"].double() ?: return@let
                vehicles += RtVehicle(
                    vehicleId = v["vehicle"].obj()?.get("id").str(),
                    tripId = v["trip"].obj()?.get("tripId").str(),
                    latitude = la,
                    longitude = lo,
                    bearing = p["bearing"].double()?.toFloat(),
                    speed = p["speed"].double()?.toFloat(),
                    timestamp = v["timestamp"].long(),
                    occupancy = occupancyOf(v["occupancyStatus"]),
                )
            }
            o["alert"].obj()?.let { a ->
                fun texts(e: JsonElement?) = e.obj()?.get("translation").arr().mapNotNull { t ->
                    val to = t.obj() ?: return@mapNotNull null
                    to["text"].str()?.takeIf { it.isNotBlank() }?.let { RtText(to["language"].str(), it) }
                }
                val header = texts(a["headerText"])
                val description = texts(a["descriptionText"])
                if (header.isEmpty() && description.isEmpty()) return@let
                val entities = a["informedEntity"].arr().mapNotNull { it.obj() }
                val periods = (a["activePeriod"].arr() + a["impactPeriod"].arr()).mapNotNull { it.obj() }
                    .map { it["start"].long() to it["end"].long() }
                alerts += RtAlert(
                    id = o["id"].str(),
                    header = header,
                    description = description,
                    stops = entities.mapNotNull { it["stopId"].str() }.toSet(),
                    routes = entities.mapNotNull { it["routeId"].str() }.toSet(),
                    trips = entities.mapNotNull { it["trip"].obj()?.get("tripId").str() }.toSet(),
                    periods = periods,
                    effect = a["effect"]?.let { e -> (e as? JsonPrimitive)?.let { it.longOrNull?.toInt() ?: EFFECTS.indexOf(it.content).takeIf { i -> i > 0 } } },
                )
            }
        }
        return RtFeed(root["header"].obj()?.get("timestamp").long(), trips, vehicles, alerts)
    }

    /** OccupancyStatus by name (JSON) in the order of its numbers. */
    private val OCCUPANCY = listOf(
        "EMPTY", "MANY_SEATS_AVAILABLE", "FEW_SEATS_AVAILABLE", "STANDING_ROOM_ONLY", "CRUSHED_STANDING_ROOM_ONLY",
        "FULL", "NOT_ACCEPTING_PASSENGERS", "NO_DATA_AVAILABLE", "NOT_BOARDABLE",
    )

    /** Effect by name (JSON); index = number (0 is unused). */
    private val EFFECTS = listOf(
        "", "NO_SERVICE", "REDUCED_SERVICE", "SIGNIFICANT_DELAYS", "DETOUR", "ADDITIONAL_SERVICE", "MODIFIED_SERVICE",
        "OTHER_EFFECT", "UNKNOWN_EFFECT", "STOP_MOVED", "NO_EFFECT", "ACCESSIBILITY_ISSUE",
    )

    private fun occupancyOf(e: JsonElement?): Int? {
        val p = e as? JsonPrimitive ?: return null
        return p.longOrNull?.toInt() ?: OCCUPANCY.indexOf(p.content).takeIf { it >= 0 }
    }
}
