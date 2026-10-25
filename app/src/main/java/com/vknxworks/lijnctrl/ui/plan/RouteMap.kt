package com.vknxworks.lijnctrl.ui.plan

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.DirectionsBus
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.ui.components.friendlyMessage
import kotlinx.coroutines.delay
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.data.lines.LineInfo
import com.vknxworks.lijnctrl.data.location.Locator
import com.vknxworks.lijnctrl.data.plan.Journey
import com.vknxworks.lijnctrl.data.plan.LatLon
import com.vknxworks.lijnctrl.ui.components.MessageView
import com.vknxworks.lijnctrl.ui.components.ScreenTopBar
import com.vknxworks.lijnctrl.ui.components.clockTime
import com.vknxworks.lijnctrl.ui.components.rememberLineInfo
import com.vknxworks.lijnctrl.ui.nav.JourneyRoute
import com.vknxworks.lijnctrl.ui.nav.LocalNavigator
import com.vknxworks.lijnctrl.ui.nearby.configureOsm
import com.vknxworks.lijnctrl.ui.theme.Gutter
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

private fun hexColor(value: String?): Color? = value?.removePrefix("#")?.takeIf { it.length == 6 }?.let {
    runCatching { Color(0xFF000000 or it.toLong(16)) }.getOrNull()
}

/** The colour a ride is drawn in: the line's own colour, unless it's too pale to see on the map. */
private fun rideColor(info: LineInfo?, fallback: Color): Color {
    val bg = hexColor(info?.background)
    if (bg != null && bg.luminance() < 0.85f) return bg
    val other = hexColor(info?.backgroundBorder) ?: hexColor(info?.foreground)
    return other?.takeIf { it.luminance() < 0.85f } ?: fallback
}

private fun dot(fill: Int, stroke: Int, sizePx: Int, strokePx: Int) = GradientDrawable().apply {
    shape = GradientDrawable.OVAL
    setColor(fill)
    setStroke(strokePx, stroke)
    setSize(sizePx, sizePx)
}

private fun LatLon.geo() = GeoPoint(latitude, longitude)

/** An extra stop to show on the map (the trip page shows every stop of the trip). */
data class MapStop(val point: LatLon, val name: String, val stop: com.vknxworks.lijnctrl.data.api.StopKey?)

/** What the live-vehicle chip on the map says. */
private sealed interface LiveState {
    data object Searching : LiveState
    data class Found(val count: Int, val newest: java.time.Instant?) : LiveState
    data object NotFound : LiveState
    data class Failed(val message: String) : LiveState
}

/**
 * A bus or tram on the map: a circle in the line's colour with its number, and a small pointer showing which way
 * it's heading (when De Lijn sends that).
 */
private fun busIcon(context: android.content.Context, label: String, fill: Int, text: Int, bearing: Float?, density: Float): android.graphics.drawable.Drawable {
    val size = (52 * density).toInt()
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val c = size / 2f
    val r = 16 * density
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    if (bearing != null) {
        canvas.save()
        canvas.rotate(bearing, c, c)
        val path = android.graphics.Path().apply {
            moveTo(c, c - r - 9 * density)
            lineTo(c - 7 * density, c - r + 3 * density)
            lineTo(c + 7 * density, c - r + 3 * density)
            close()
        }
        paint.color = android.graphics.Color.WHITE
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3 * density
        paint.strokeJoin = Paint.Join.ROUND
        canvas.drawPath(path, paint)
        paint.style = Paint.Style.FILL
        paint.color = fill
        canvas.drawPath(path, paint)
        canvas.restore()
    }
    paint.style = Paint.Style.FILL
    paint.color = fill
    canvas.drawCircle(c, c, r, paint)
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = 3 * density
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(c, c, r, paint)
    paint.style = Paint.Style.FILL
    paint.color = text
    paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    paint.textSize = (if (label.length <= 2) 14 else if (label.length == 3) 12 else 10) * density
    val y = c - (paint.descent() + paint.ascent()) / 2
    canvas.drawText(label.take(4), c, y, paint)
    return android.graphics.drawable.BitmapDrawable(context.resources, bmp)
}

/**
 * A route on an OpenStreetMap map: walks as dotted lines, rides in their line's colour, the start and end, and
 * where you get on and off. With [interactive] off it's a still preview (pan and zoom are off); tap handling is
 * then up to the caller.
 */
@Composable
fun RouteMap(
    journey: Journey,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    extraStops: List<MapStop> = emptyList(),
    /** What to look for on the road for each ride, in order; by default worked out from the rides' stops and times. */
    liveTargets: List<com.vknxworks.lijnctrl.data.live.LiveTarget?>? = null,
    /** Zoom to the vehicle the first time it's found (the trip page). */
    focusOnBus: Boolean = false,
    /** More vehicles to show, drawn like the (first) ride: the line page shows every vehicle on the line. */
    extraVehicles: List<com.vknxworks.lijnctrl.data.live.LiveTarget> = emptyList(),
) {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    val density = LocalDensity.current
    val lines = rememberLineInfo(journey.legs.mapNotNull { it.line })
    fun px(dp: Float) = with(density) { dp.dp.toPx() }

    val map = remember {
        configureOsm(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(interactive)
            isClickable = interactive
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 7.0
            maxZoomLevel = 19.5
            controller.setZoom(9.0)
            controller.setCenter(GeoPoint(51.03, 4.40))
        }
    }

    // Light map tiles turned dark in a dark theme, as on the Nearby map.
    LaunchedEffect(dark) {
        map.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        map.invalidate()
    }

    // Your position as a dot that moves with you (Android's own location service), when location is allowed.
    var hasLocation by remember { mutableStateOf(Locator.hasPermission(context)) }
    var myLocation by remember { mutableStateOf<MyLocationNewOverlay?>(null) }
    var following by remember { mutableStateOf(false) }
    DisposableEffect(map, hasLocation) {
        val overlay = if (interactive && hasLocation) {
            MyLocationNewOverlay(GpsMyLocationProvider(context), map).also {
                it.enableMyLocation()
                map.overlays.add(it)
            }
        } else null
        myLocation = overlay
        onDispose {
            overlay?.disableFollowLocation()
            overlay?.disableMyLocation()
            overlay?.let { map.overlays.remove(it) }
        }
    }
    DisposableEffect(map) { onDispose { map.onDetach() } }
    // Moving the map yourself stops following, as in other map apps.
    DisposableEffect(map) {
        val listener = object : org.osmdroid.events.MapListener {
            override fun onScroll(event: org.osmdroid.events.ScrollEvent?): Boolean {
                if (following && myLocation?.isFollowLocationEnabled == false) following = false
                return false
            }
            override fun onZoom(event: org.osmdroid.events.ZoomEvent?): Boolean = false
        }
        map.addMapListener(listener)
        onDispose { map.removeMapListener(listener) }
    }
    fun follow() {
        val o = myLocation ?: return
        if (following) {
            o.disableFollowLocation()
            following = false
        } else {
            o.enableFollowLocation()
            if (map.zoomLevelDouble < 16.0) map.controller.zoomTo(17.0)
            following = true
        }
    }
    val permission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted -> if (granted.values.any { it }) hasLocation = true }
    var followAfterGrant by remember { mutableStateOf(false) }
    LaunchedEffect(myLocation) { if (followAfterGrant && myLocation != null) { followAfterGrant = false; follow() } }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val primary = cs.primary
    // ---------- Live vehicles (needs the GTFS Realtime key) ----------
    val c = context.container
    val gtfsKey by c.gtfsKey.key.collectAsStateWithLifecycle()
    val busMarkers = remember { mutableListOf<Marker>() }
    var liveState by remember { mutableStateOf<LiveState?>(null) }
    var focusedBus by remember { mutableStateOf(false) }
    val rideLegs = remember(journey) { journey.legs.filter { !it.isWalk } }
    // Vehicles beyond the journey's own rides (the line page) take the first ride's line and route.
    fun legFor(ride: Int) = rideLegs.getOrElse(ride) { rideLegs.first() }
    val targets = remember(journey, liveTargets, extraVehicles) {
        val now = java.time.Instant.now()
        rideLegs.mapIndexed<com.vknxworks.lijnctrl.data.plan.JourneyLeg, com.vknxworks.lijnctrl.data.live.LiveTarget?> { i, leg ->
            liveTargets?.getOrNull(i) ?: run {
                // Rides that haven't ended and start within the next hour and a half.
                if (leg.end.isBefore(now.minusSeconds(120)) || leg.start.isAfter(now.plusSeconds(90 * 60))) return@run null
                val stops = buildMap {
                    leg.fromStop?.let { put(it.number, leg.start) }
                    leg.toStop?.let { put(it.number, leg.end) }
                }
                if (stops.isEmpty()) null else com.vknxworks.lijnctrl.data.live.LiveTarget(stops, leg.trip)
            }
        } + extraVehicles
    }
    val wantsLive = interactive && targets.any { it != null }
    // One smooth track and one marker per ride being followed (keyed by the ride's position in the journey).
    val tracks = remember(journey) { mutableMapOf<Int, BusTrack>() }
    val markerByRide = remember(journey) { mutableMapOf<Int, Marker>() }
    val iconBucket = remember(journey) { mutableMapOf<Int, Int>() }
    fun clearBuses() {
        map.overlays.removeAll(busMarkers)
        busMarkers.clear()
        markerByRide.clear()
        tracks.clear()
        iconBucket.clear()
        map.invalidate()
    }
    fun styleOf(ride: Int): Triple<String, Int, Int> {
        val leg = legFor(ride)
        val info = legBadgeInfo(leg, lines)
        val fill = rideColor(info, primary).toArgb()
        val text = if (Color(fill).luminance() > 0.5f) android.graphics.Color.BLACK else android.graphics.Color.WHITE
        return Triple(info?.publicNumber ?: leg.badge ?: "", fill, text)
    }
    fun applyFix(ride: Int, fix: com.vknxworks.lijnctrl.data.live.LiveFix, nowMs: Long) {
        val track = tracks.getOrPut(ride) {
            BusTrack(legFor(ride).path.filter { it.latitude != 0.0 }.takeIf { it.size >= 2 }?.let { PathGeometry(it) })
        }
        track.update(fix, nowMs)
        if (ride !in markerByRide) {
            val m = Marker(map).apply {
                title = legFor(ride).badge
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ -> true }
            }
            markerByRide[ride] = m
            busMarkers += m
            map.overlays.add(m)
        }
    }

    // Finding the vehicles: the whole feed now and then; in between, each found vehicle on its own, every few seconds.
    LaunchedEffect(targets, gtfsKey) {
        clearBuses()
        if (!wantsLive || gtfsKey.isNullOrBlank()) {
            liveState = null
            return@LaunchedEffect
        }
        liveState = LiveState.Searching
        var lastFull = 0L
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val now = System.currentTimeMillis()
                val followed = tracks.filterValues { it.vehicleId != null }
                val wanted = targets.withIndex().filter { it.value != null }
                val sinceFull = now - lastFull
                // The whole feed: to find vehicles not found yet (every 12 s), and every 45 s in case one changed trips.
                val fast = c.live.canFollowFast && followed.isNotEmpty() &&
                    sinceFull < com.vknxworks.lijnctrl.data.live.LiveVehicles.RELOCATE_MS &&
                    (followed.size == wanted.size || sinceFull < com.vknxworks.lijnctrl.data.live.LiveVehicles.REFRESH_MS)
                val result = runCatching {
                    if (fast) {
                        followed.forEach { (ride, track) -> c.live.follow(track.vehicleId!!)?.let { applyFix(ride, it, System.currentTimeMillis()) } }
                    } else {
                        val fixes = c.live.locate(wanted.map { it.value!! })
                        lastFull = System.currentTimeMillis()
                        fixes.forEachIndexed { j, fix -> fix?.let { applyFix(wanted[j].index, it, lastFull) } }
                    }
                }
                result.onFailure { e -> if (tracks.isEmpty()) liveState = LiveState.Failed(e.friendlyMessage()) }
                result.onSuccess {
                    val live = tracks.values.filter { it.hasFix }
                    liveState = if (live.isEmpty()) LiveState.NotFound else LiveState.Found(live.size, live.mapNotNull { it.reportedAt }.maxOrNull())
                    if (focusOnBus && !focusedBus && !following && live.isNotEmpty()) {
                        focusedBus = true
                        val (lat, lon, _) = live.first().position(System.currentTimeMillis())
                        map.controller.animateTo(GeoPoint(lat, lon), maxOf(map.zoomLevelDouble, 15.0), 600L)
                    }
                }
                val tracking = tracks.values.any { it.vehicleId != null }
                delay(
                    if (tracking && c.live.canFollowFast) com.vknxworks.lijnctrl.data.live.LiveVehicles.FAST_MS
                    else com.vknxworks.lijnctrl.data.live.LiveVehicles.REFRESH_MS,
                )
            }
        }
    }

    // Drawing the vehicles: about 12 times a second, so they move smoothly between reports.
    LaunchedEffect(targets, gtfsKey, lines, dark) {
        if (!wantsLive || gtfsKey.isNullOrBlank()) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                val now = System.currentTimeMillis()
                var moved = false
                tracks.forEach { (ride, track) ->
                    val m = markerByRide[ride] ?: return@forEach
                    if (!track.hasFix) return@forEach
                    val (lat, lon, heading) = track.position(now)
                    m.position = GeoPoint(lat, lon)
                    // The icon (with its heading pointer) is only redrawn when the heading changes noticeably.
                    val bucket = heading?.let { Math.round(it / 15f) } ?: -1
                    if (iconBucket[ride] != bucket) {
                        iconBucket[ride] = bucket
                        val (label, fill, text) = styleOf(ride)
                        m.icon = busIcon(context, label, fill, text, heading, density.density)
                    }
                    moved = true
                }
                if (moved) map.invalidate()
                delay(80)
            }
        }
    }

    val walkColor = if (dark) Color(0xFFBDBDBD) else Color(0xFF616161)
    val casing = if (dark) Color(0xFF121212) else Color.White
    LaunchedEffect(journey, lines, dark) {
        map.overlays.removeAll { it is Polyline || (it is Marker && it !in busMarkers) }
        map.overlays.removeAll(busMarkers)
        val all = mutableListOf<GeoPoint>()

        journey.legs.forEach { leg ->
            val pts = leg.path.filter { it.latitude != 0.0 && it.longitude != 0.0 }.map { it.geo() }
            if (pts.size < 2) return@forEach
            all += pts
            if (leg.isWalk) {
                map.overlays.add(
                    Polyline().apply {
                        setPoints(pts)
                        infoWindow = null
                        outlinePaint.apply {
                            color = walkColor.toArgb()
                            strokeWidth = px(4f)
                            strokeCap = Paint.Cap.ROUND
                            pathEffect = DashPathEffect(floatArrayOf(px(2f), px(7f)), 0f)
                        }
                    },
                )
            } else {
                val color = rideColor(legBadgeInfo(leg, lines), primary)
                // A thin edge around the line, so yellow and light lines stay visible on the map.
                map.overlays.add(
                    Polyline().apply {
                        setPoints(pts)
                        infoWindow = null
                        outlinePaint.apply {
                            this.color = casing.toArgb()
                            strokeWidth = px(9f)
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }
                    },
                )
                map.overlays.add(
                    Polyline().apply {
                        setPoints(pts)
                        infoWindow = null
                        outlinePaint.apply {
                            this.color = color.toArgb()
                            strokeWidth = px(6f)
                            strokeCap = Paint.Cap.ROUND
                            strokeJoin = Paint.Join.ROUND
                        }
                    },
                )
            }
        }

        // Every stop of the trip, as small dots (the trip page).
        val smallDot = dot(android.graphics.Color.WHITE, android.graphics.Color.GRAY, px(10f).toInt(), px(2f).toInt())
        extraStops.forEach { s ->
            if (s.point.latitude == 0.0) return@forEach
            all += s.point.geo()
            map.overlays.add(
                Marker(map).apply {
                    position = s.point.geo()
                    title = s.name
                    icon = smallDot
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ ->
                        if (interactive && s.stop != null) nav.stop(s.stop.entity, s.stop.number, s.name)
                        true
                    }
                },
            )
        }

        // Where you get on and off each ride: tap one to open the stop (when it's a De Lijn stop).
        val rides = journey.legs.filter { !it.isWalk && it.path.size >= 2 }
        val stopDot = dot(android.graphics.Color.WHITE, android.graphics.Color.DKGRAY, px(14f).toInt(), px(2.5f).toInt())
        rides.forEach { leg ->
            listOf(Triple(leg.path.first(), leg.fromName, leg.fromStop), Triple(leg.path.last(), leg.toName, leg.toStop)).forEach { (p, name, key) ->
                map.overlays.add(
                    Marker(map).apply {
                        position = p.geo()
                        title = name
                        icon = stopDot
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        setOnMarkerClickListener { _, _ ->
                            if (interactive && key != null) nav.stop(key.entity, key.number, name)
                            true
                        }
                    },
                )
            }
        }

        // The start (a ring) and the end (a filled dot), drawn last so they sit on top.
        val first = journey.legs.firstOrNull { it.path.isNotEmpty() }?.path?.first()
        val last = journey.legs.lastOrNull { it.path.isNotEmpty() }?.path?.last()
        first?.let {
            map.overlays.add(
                Marker(map).apply {
                    position = it.geo()
                    title = journey.legs.first().fromName
                    icon = dot(android.graphics.Color.WHITE, primary.toArgb(), px(22f).toInt(), px(5f).toInt())
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ -> true }
                },
            )
        }
        last?.let {
            map.overlays.add(
                Marker(map).apply {
                    position = it.geo()
                    title = journey.legs.last().toName
                    icon = dot(primary.toArgb(), android.graphics.Color.WHITE, px(24f).toInt(), px(3f).toInt())
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ -> true }
                },
            )
        }
        // Vehicles stay on top of everything else.
        map.overlays.addAll(busMarkers)
        map.invalidate()

        // Fits the whole route on screen, once the map knows its size.
        // (Not while following you: the map stays on your position.)
        if (all.isNotEmpty() && !following) {
            val fit = {
                if (all.size == 1) {
                    map.controller.setZoom(16.0)
                    map.controller.setCenter(all.first())
                } else {
                    val box = BoundingBox.fromGeoPointsSafe(all)
                    map.zoomToBoundingBox(box, false, px(36f).toInt())
                }
            }
            if (map.width > 0 && map.height > 0) fit() else map.addOnFirstLayoutListener { _, _, _, _, _ -> fit() }
        }
    }

    Box(modifier) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())
        if (interactive) {
            androidx.compose.material3.SmallFloatingActionButton(
                onClick = {
                    if (hasLocation) follow()
                    else {
                        followAfterGrant = true
                        com.vknxworks.lijnctrl.ui.lock.AppLock.expectReturn()
                        permission.launch(arrayOf(android.Manifest.permission.ACCESS_COARSE_LOCATION, android.Manifest.permission.ACCESS_FINE_LOCATION))
                    }
                },
                shape = com.vknxworks.lijnctrl.ui.theme.CtrlShapes.Square,
                elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(0.dp, 0.dp, 0.dp, 0.dp),
                containerColor = if (following) com.vknxworks.lijnctrl.ui.theme.CtrlColors.Accent else cs.surface,
                contentColor = if (following) com.vknxworks.lijnctrl.ui.theme.CtrlColors.OnAccent else cs.onSurface,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                Icon(
                    if (following) Icons.Filled.MyLocation else Icons.Outlined.MyLocation,
                    stringResource(R.string.follow_my_location),
                )
            }
        }
        // Whether the bus or tram was found live, or how to turn live positions on.
        if (wantsLive) {
            val now by com.vknxworks.lijnctrl.ui.components.rememberNow()
            val state = liveState
            val text = when {
                gtfsKey.isNullOrBlank() -> stringResource(R.string.live_vehicles_need_key)
                state is LiveState.Searching || state == null -> stringResource(R.string.live_vehicle_searching)
                state is LiveState.NotFound -> stringResource(R.string.live_vehicle_not_found)
                state is LiveState.Failed -> state.message
                state is LiveState.Found -> {
                    val ago = state.newest?.let { ((now.epochSecond - it.epochSecond).coerceAtLeast(0)).toInt() }
                    if (ago == null) stringResource(R.string.live_vehicle_found)
                    else if (ago < 60) stringResource(R.string.live_vehicle_seconds_ago, ago)
                    else stringResource(R.string.live_vehicle_minutes_ago, ago / 60)
                }
                else -> ""
            }
            Surface(
                shape = com.vknxworks.lijnctrl.ui.theme.CtrlShapes.Control,
                color = cs.surface.copy(alpha = 0.94f),
                border = com.vknxworks.lijnctrl.ui.theme.ControlBorder,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 16.dp, end = 16.dp)
                    .then(if (gtfsKey.isNullOrBlank()) Modifier.clickable { nav.navigate(com.vknxworks.lijnctrl.ui.nav.GtfsKeyRoute) } else Modifier),
            ) {
                Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.DirectionsBus,
                        null,
                        Modifier.size(16.dp),
                        tint = if (liveState is LiveState.Found) cs.primary else cs.onSurfaceVariant,
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
                    Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 2)
                }
            }
        }
        // OpenStreetMap's licence asks for this credit on the map.
        Text(
            stringResource(R.string.osm_credit),
            style = com.vknxworks.lijnctrl.ui.theme.CtrlType.DataSmall,
            color = cs.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(cs.background.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** A small, still map of the route at the top of the route's page; tap it for the full map. */
@Composable
fun RouteMapPreview(journey: Journey, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(200.dp).clip(com.vknxworks.lijnctrl.ui.theme.CtrlShapes.Card).background(cs.surface).border(1.dp, com.vknxworks.lijnctrl.ui.theme.CtrlColors.Outline, com.vknxworks.lijnctrl.ui.theme.CtrlShapes.Card)) {
        RouteMap(journey, Modifier.fillMaxSize(), interactive = false)
        // On top of the map, so a tap opens the full map instead of reaching the (still) map.
        Box(Modifier.fillMaxSize().clickable(onClick = onOpen))
        Surface(
            shape = com.vknxworks.lijnctrl.ui.theme.CtrlShapes.Square,
            color = cs.surface.copy(alpha = 0.94f),
            border = com.vknxworks.lijnctrl.ui.theme.ControlBorder,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
        ) {
            Icon(Icons.Outlined.OpenInFull, stringResource(R.string.open_map), Modifier.padding(8.dp).size(18.dp))
        }
    }
}

/**
 * The route planner's map: the routes found, one at a time, on a full-screen map. The chips at the bottom switch
 * between routes; the card under them opens the chosen route's details.
 */
@Composable
fun RouteMapScreen(index: Int) {
    val nav = LocalNavigator.current
    val results = PlanState.results.orEmpty()
    var selected by rememberSaveable { mutableIntStateOf(index) }
    if (selected !in results.indices) selected = 0

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { ScreenTopBar(stringResource(R.string.route_map)) },
    ) { padding ->
        val journey = results.getOrNull(selected)
        if (journey == null) {
            MessageView(Icons.Outlined.Directions, stringResource(R.string.route_gone), stringResource(R.string.route_gone_body), Modifier.padding(padding))
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize()) {
            RouteMap(journey, Modifier.weight(1f).fillMaxWidth())
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(top = 8.dp, bottom = 12.dp)) {
                if (results.size > 1) {
                    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = selected)
                    LazyRow(
                        state = rowState,
                        contentPadding = PaddingValues(horizontal = Gutter),
                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(results) { i, j ->
                            com.vknxworks.lijnctrl.ui.components.CtrlChip(
                                selected = i == selected,
                                onClick = { selected = i },
                                label = { Text("${clockTime(j.start)} → ${clockTime(j.end)}") },
                            )
                        }
                    }
                }
                Box(Modifier.padding(horizontal = Gutter, vertical = 8.dp)) {
                    JourneyCard(journey) { nav.navigate(JourneyRoute(selected)) }
                }
            }
        }
    }
}
