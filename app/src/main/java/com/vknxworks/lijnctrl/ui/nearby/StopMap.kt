package com.vknxworks.lijnctrl.ui.nearby

import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vknxworks.lijnctrl.BuildConfig
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.data.api.ApiNearbyStop
import com.vknxworks.lijnctrl.data.api.DeLijnApi
import com.vknxworks.lijnctrl.data.location.Locator
import com.vknxworks.lijnctrl.ui.components.LocalBottomClearance
import com.vknxworks.lijnctrl.ui.components.friendlyMessage
import com.vknxworks.lijnctrl.ui.nav.LocalNavigator
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File

/** Below this zoom level the map shows too much ground to list stops; it asks you to zoom in. */
private const val MIN_STOP_ZOOM = 15.0

/** Where the map opens when no location is known: the middle of Flanders. */
private val FLANDERS = GeoPoint(51.03, 4.40)

/** The last place the map looked at, so it opens there again. */
internal object MapMemory {
    var center: GeoPoint? = null
    var zoom: Double = 16.0
}

/** Map tiles are cached in the app's own cache folder; requests say which app they're from, as OpenStreetMap asks. */
internal fun configureOsm(context: Context) {
    Configuration.getInstance().apply {
        userAgentValue = "LijnCtrl/${BuildConfig.VERSION_NAME} (${context.packageName})"
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
    }
}

/**
 * De Lijn stops on an OpenStreetMap map. Stops load for the area you're looking at once you've zoomed in far
 * enough; tap one to open it. Map tiles come from tile.openstreetmap.org; stops still come from De Lijn.
 */
@Composable
fun StopMap(start: GeoPoint?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val dark = cs.background.luminance() < 0.5f
    val markerColor = cs.primary.toArgb()

    var zoomedOut by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var moves by remember { mutableIntStateOf(0) }
    val stops = remember { linkedMapOf<String, ApiNearbyStop>() }

    val map = remember {
        configureOsm(context)
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            isTilesScaledToDpi = true
            minZoomLevel = 8.0
            maxZoomLevel = 19.5
            val center = start ?: MapMemory.center
            controller.setZoom(if (center != null) MapMemory.zoom.coerceAtLeast(MIN_STOP_ZOOM) else 9.0)
            controller.setCenter(center ?: FLANDERS)
        }
    }

    // Light map tiles turned dark in a dark theme, so the map doesn't glare at night.
    LaunchedEffect(dark) {
        map.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
        map.invalidate()
    }

    // Your position as a dot, when location is allowed (Android's own location service).
    DisposableEffect(map) {
        val overlay = if (Locator.hasPermission(context)) {
            MyLocationNewOverlay(GpsMyLocationProvider(context), map).also {
                it.enableMyLocation()
                map.overlays.add(it)
            }
        } else null
        val listener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean { moves++; return false }
            override fun onZoom(event: ZoomEvent?): Boolean { moves++; return false }
        }
        map.addMapListener(listener)
        onDispose {
            overlay?.disableMyLocation()
            map.removeMapListener(listener)
            MapMemory.center = map.mapCenter as? GeoPoint ?: GeoPoint(map.mapCenter.latitude, map.mapCenter.longitude)
            MapMemory.zoom = map.zoomLevelDouble
            map.onDetach()
        }
    }

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

    fun showMarkers() {
        map.overlays.removeAll { it is Marker }
        stops.values.forEach { s ->
            val coord = s.coordinate ?: return@forEach
            if (coord.latitude == 0.0) return@forEach
            val entity = DeLijnApi.entityFromLinks(s) ?: s.id.take(1)
            map.overlays.add(
                Marker(map).apply {
                    position = GeoPoint(coord.latitude, coord.longitude)
                    title = s.name ?: s.id
                    icon = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(markerColor)
                        setStroke(4, android.graphics.Color.WHITE)
                        setSize(40, 40)
                    }
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    setOnMarkerClickListener { _, _ ->
                        nav.stop(entity, s.id, s.name)
                        true
                    }
                },
            )
        }
        map.invalidate()
    }

    // Stops for the area on screen, a moment after the map stops moving.
    LaunchedEffect(map) {
        snapshotFlow { moves }.debounce(600).distinctUntilChanged().collect {
            val zoom = map.zoomLevelDouble
            zoomedOut = zoom < MIN_STOP_ZOOM
            if (zoomedOut) return@collect
            val center = map.mapCenter
            // Covers the visible area: half the screen's diagonal, within what De Lijn allows.
            val box = map.boundingBox
            val radius = (box.diagonalLengthInMeters / 2).toInt().coerceIn(300, 2000)
            loading = true
            runCatching { c.api.stopsNearby(center.latitude, center.longitude, max = 50, radius = radius) }
                .onSuccess { r ->
                    error = null
                    r.stops.filter { it.type == null || it.type.equals("DELIJN", true) }.forEach { stops[it.id] = it }
                    // Keeps the map light: only the most recent few hundred stops stay as markers.
                    while (stops.size > 400) stops.remove(stops.keys.first())
                    showMarkers()
                }
                .onFailure { error = it.friendlyMessage() }
            loading = false
        }
    }
    LaunchedEffect(Unit) { moves++ }

    Box(modifier.fillMaxSize()) {
        AndroidView(factory = { map }, modifier = Modifier.fillMaxSize())

        Column(Modifier.align(Alignment.TopCenter).padding(top = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            val message = when {
                zoomedOut -> stringResource(R.string.zoom_in_for_stops)
                error != null -> error
                else -> null
            }
            if (message != null || loading) {
                Surface(shape = RoundedCornerShape(50), color = cs.surfaceContainerHigh, tonalElevation = 4.dp, shadowElevation = 4.dp) {
                    Box(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                        if (message != null) Text(message, style = MaterialTheme.typography.labelLarge)
                        else CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                scope.launch {
                    if (!Locator.hasPermission(context)) return@launch
                    Locator.current(context)?.let { loc ->
                        map.controller.animateTo(GeoPoint(loc.latitude, loc.longitude), 17.0, 600L)
                    }
                }
            },
            shape = CircleShape,
            containerColor = cs.primaryContainer,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp + LocalBottomClearance.current),
        ) { Icon(Icons.Outlined.MyLocation, stringResource(R.string.my_location)) }

        // OpenStreetMap's licence asks for this credit on the map.
        Text(
            stringResource(R.string.osm_credit),
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurface,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 8.dp + LocalBottomClearance.current)
                .background(cs.surface.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
