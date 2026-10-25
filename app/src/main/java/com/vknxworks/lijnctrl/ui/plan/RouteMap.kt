package com.vknxworks.lijnctrl.ui.plan

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FilterChip
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

/**
 * A route on an OpenStreetMap map: walks as dotted lines, rides in their line's colour, the start and end, and
 * where you get on and off. With [interactive] off it's a still preview (pan and zoom are off); tap handling is
 * then up to the caller.
 */
@Composable
fun RouteMap(journey: Journey, modifier: Modifier = Modifier, interactive: Boolean = true) {
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
    val walkColor = if (dark) Color(0xFFBDBDBD) else Color(0xFF616161)
    val casing = if (dark) Color(0xFF121212) else Color.White
    LaunchedEffect(journey, lines, dark) {
        map.overlays.removeAll { it is Polyline || it is Marker }
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
                shape = CircleShape,
                containerColor = if (following) cs.primary else cs.surfaceContainerHigh,
                contentColor = if (following) cs.onPrimary else cs.onSurface,
                modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp),
            ) {
                Icon(
                    if (following) Icons.Filled.MyLocation else Icons.Outlined.MyLocation,
                    stringResource(R.string.follow_my_location),
                )
            }
        }
        // OpenStreetMap's licence asks for this credit on the map.
        Text(
            stringResource(R.string.osm_credit),
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurface,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(6.dp)
                .background(cs.surface.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** A small, still map of the route at the top of the route's page; tap it for the full map. */
@Composable
fun RouteMapPreview(journey: Journey, onOpen: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().height(200.dp).clip(MaterialTheme.shapes.large).background(cs.surfaceContainer)) {
        RouteMap(journey, Modifier.fillMaxSize(), interactive = false)
        // On top of the map, so a tap opens the full map instead of reaching the (still) map.
        Box(Modifier.fillMaxSize().clickable(onClick = onOpen))
        Surface(
            shape = CircleShape,
            color = cs.surfaceContainerHigh.copy(alpha = 0.9f),
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
                            FilterChip(
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
