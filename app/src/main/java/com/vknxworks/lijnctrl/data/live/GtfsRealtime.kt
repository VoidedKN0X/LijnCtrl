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
)

data class RtFeed(val timestamp: Long?, val trips: List<RtTrip>, val vehicles: List<RtVehicle>) {
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
            java.util.zip.GZIPInputStream(raw.inputStream()).use { it.readBytes() }
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
            val len = varint().toInt()
            if (len < 0 || pos + len > end) throw IllegalArgumentException("bad length")
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
        r.fields { f, w ->
            when {
                f == 1 && w == 2 -> r.sub().let { h -> h.fields { hf, hw -> if (hf == 3 && hw == 0) timestamp = h.varint() else h.skip(hw) } }
                f == 2 && w == 2 -> entity(r.sub(), trips, vehicles)
                else -> r.skip(w)
            }
        }
        return RtFeed(timestamp, trips, vehicles)
    }

    private fun entity(r: Reader, trips: MutableList<RtTrip>, vehicles: MutableList<RtVehicle>) {
        r.fields { f, w ->
            when {
                f == 3 && w == 2 -> tripUpdate(r.sub())?.let(trips::add)
                f == 4 && w == 2 -> vehiclePosition(r.sub())?.let(vehicles::add)
                else -> r.skip(w)
            }
        }
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
        r.fields { f, w ->
            when {
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
        return RtVehicle(vehicle, tripId, la, lo, bearing, speed, timestamp)
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
                )
            }
        }
        return RtFeed(root["header"].obj()?.get("timestamp").long(), trips, vehicles)
    }
}
