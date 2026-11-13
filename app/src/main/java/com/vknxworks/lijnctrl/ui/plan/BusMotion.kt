package com.vknxworks.lijnctrl.ui.plan

import com.vknxworks.lijnctrl.data.live.LiveFix
import com.vknxworks.lijnctrl.data.plan.LatLon
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sqrt

/** A route line in metres around its first point, for measuring and walking along it. */
internal class PathGeometry(points: List<LatLon>) {
    private val lat0 = points.first().latitude
    private val lon0 = points.first().longitude
    private val kx = 111_320.0 * cos(Math.toRadians(lat0))
    private val ky = 110_540.0
    private val xs = DoubleArray(points.size) { (points[it].longitude - lon0) * kx }
    private val ys = DoubleArray(points.size) { (points[it].latitude - lat0) * ky }
    private val cum = DoubleArray(points.size).also { c ->
        for (i in 1 until points.size) c[i] = c[i - 1] + hypot(xs[i] - xs[i - 1], ys[i] - ys[i - 1])
    }
    val length: Double get() = cum.last()

    /** How far along the line the point closest to (lat, lon) is, and how far the point is from the line (metres). */
    fun project(lat: Double, lon: Double): Pair<Double, Double> {
        val px = (lon - lon0) * kx
        val py = (lat - lat0) * ky
        var bestS = 0.0
        var bestD = Double.MAX_VALUE
        for (i in 0 until xs.size - 1) {
            val dx = xs[i + 1] - xs[i]
            val dy = ys[i + 1] - ys[i]
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (((px - xs[i]) * dx + (py - ys[i]) * dy) / len2).coerceIn(0.0, 1.0)
            val qx = xs[i] + t * dx
            val qy = ys[i] + t * dy
            val d = hypot(px - qx, py - qy)
            if (d < bestD) {
                bestD = d
                bestS = cum[i] + t * sqrt(len2)
            }
        }
        return bestS to bestD
    }

    /** The point [s] metres along the line, and the line's heading there (degrees from north). */
    fun pointAt(s: Double): Triple<Double, Double, Float> {
        val d = s.coerceIn(0.0, length)
        var i = cum.indexOfFirst { it >= d }.let { if (it <= 0) 1 else it }
        if (i >= cum.size) i = cum.size - 1
        val seg = cum[i] - cum[i - 1]
        val t = if (seg == 0.0) 0.0 else (d - cum[i - 1]) / seg
        val x = xs[i - 1] + t * (xs[i] - xs[i - 1])
        val y = ys[i - 1] + t * (ys[i] - ys[i - 1])
        val heading = Math.toDegrees(atan2(xs[i] - xs[i - 1], ys[i] - ys[i - 1])).toFloat().let { (it + 360f) % 360f }
        return Triple(lat0 + y / ky, lon0 + x / kx, heading)
    }
}

/**
 * Smooth movement for one bus or tram between its position reports (about every 15 seconds): it moves on along
 * its route at the speed measured from its last reports, for a short while, and glides to each new report instead
 * of jumping. Without a route line (or when the bus is off it) it only glides between reports.
 */
internal class BusTrack(private val path: PathGeometry?) {
    private var lastReportMs: Long? = null
    private var s0 = 0.0
    private var t0 = 0L
    private var speed = 0.0
    private var onPath = false
    private var offsetS = 0.0
    private var offsetSince = 0L
    // Free movement (off the route line): glide from one position to the next.
    private var fromLat = 0.0
    private var fromLon = 0.0
    private var toLat = 0.0
    private var toLon = 0.0
    private var glideSince = 0L
    private var bearing: Float? = null
    var vehicleId: String? = null
        private set
    var reportedAt: java.time.Instant? = null
        private set

    val hasFix: Boolean get() = lastReportMs != null

    fun update(fix: LiveFix, nowMs: Long) {
        val reportMs = (fix.reportedAt?.toEpochMilli() ?: nowMs).coerceAtMost(nowMs)
        vehicleId = fix.vehicleId ?: vehicleId
        if (lastReportMs != null && reportMs <= lastReportMs!!) return
        val previousReport = lastReportMs
        reportedAt = fix.reportedAt
        bearing = fix.bearing ?: bearing
        val (curLat, curLon, _) = if (previousReport != null) position(nowMs) else Triple(fix.latitude, fix.longitude, null)

        val proj = path?.project(fix.latitude, fix.longitude)
        val nowOnPath = proj != null && proj.second <= 120.0
        // Where the bus is drawn now (along the line), before anything changes, so it can glide from there.
        val shownS = if (onPath && previousReport != null) alongPath(nowMs) else null
        if (nowOnPath) {
            val s = proj!!.first
            if (onPath && previousReport != null) {
                val dt = (reportMs - previousReport) / 1000.0
                if (dt in 1.0..90.0) {
                    val measured = ((s - s0) / dt).coerceIn(0.0, 25.0)
                    speed = if (speed == 0.0) measured else 0.5 * speed + 0.5 * measured
                } else if (dt > 90.0) speed = 0.0
            } else speed = 0.0
            s0 = s
            t0 = reportMs
            val predicted = s0 + speed * extrapolationSeconds(nowMs)
            offsetS = shownS?.let { (it - predicted).takeIf { d -> kotlin.math.abs(d) < 600 } } ?: 0.0
            offsetSince = nowMs
        }
        onPath = nowOnPath
        fromLat = curLat
        fromLon = curLon
        toLat = fix.latitude
        toLon = fix.longitude
        glideSince = nowMs
        lastReportMs = reportMs
    }

    /** Moving on is capped: after this long without a report, the bus waits where it is. */
    private fun extrapolationSeconds(nowMs: Long) = ((nowMs - t0) / 1000.0).coerceIn(0.0, 15.0)

    private fun alongPath(nowMs: Long): Double {
        val decay = exp(-(nowMs - offsetSince) / 450.0)
        return s0 + speed * extrapolationSeconds(nowMs) + offsetS * decay
    }

    /** Where to draw the bus now, and which way it's heading. */
    fun position(nowMs: Long): Triple<Double, Double, Float?> {
        if (onPath && path != null) {
            val (lat, lon, heading) = path.pointAt(alongPath(nowMs))
            return Triple(lat, lon, heading)
        }
        val t = ((nowMs - glideSince) / 900.0).coerceIn(0.0, 1.0)
        val e = t * t * (3 - 2 * t)
        return Triple(fromLat + (toLat - fromLat) * e, fromLon + (toLon - fromLon) * e, bearing)
    }
}
