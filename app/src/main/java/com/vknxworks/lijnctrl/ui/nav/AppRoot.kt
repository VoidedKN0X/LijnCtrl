package com.vknxworks.lijnctrl.ui.nav

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Directions
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.NavigationRailItemDefaults
import com.vknxworks.lijnctrl.ui.theme.CtrlColors
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.vknxworks.lijnctrl.R
import com.vknxworks.lijnctrl.container
import com.vknxworks.lijnctrl.ui.components.LocalBottomClearance
import com.vknxworks.lijnctrl.ui.lines.LineScreen
import com.vknxworks.lijnctrl.ui.lines.TripScreen
import com.vknxworks.lijnctrl.ui.nearby.NearbyScreen
import com.vknxworks.lijnctrl.ui.plan.JourneyScreen
import com.vknxworks.lijnctrl.ui.plan.PickPlaceScreen
import com.vknxworks.lijnctrl.ui.plan.PlanScreen
import com.vknxworks.lijnctrl.ui.search.PlaceScreen
import com.vknxworks.lijnctrl.ui.search.TownScreen
import com.vknxworks.lijnctrl.ui.stops.TimetableScreen
import com.vknxworks.lijnctrl.ui.search.SearchScreen
import com.vknxworks.lijnctrl.ui.settings.SettingsScreen
import com.vknxworks.lijnctrl.ui.settings.YouScreen
import com.vknxworks.lijnctrl.ui.setup.ApiKeyScreen
import com.vknxworks.lijnctrl.ui.stops.StopScreen
import com.vknxworks.lijnctrl.ui.stops.StopsScreen

@Composable
fun AppRoot() {
    val c = LocalContext.current.container
    val hasKey by c.apiKey.hasKey.collectAsStateWithLifecycle()
    val key by c.apiKey.key.collectAsStateWithLifecycle()
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        when {
            // No key yet (or the saved one can't be read any more): set it up first.
            !hasKey || key == null -> ApiKeyScreen(firstRun = true, unreadable = hasKey && key == null, onDone = {}, onCancel = null)
            else -> MainScaffold()
        }
    }
}

private data class TopDest(val route: Any, val item: TabItem)

private val topDestinations = listOf(
    TopDest(StopsRoute, TabItem(R.string.tab_stops, Icons.Outlined.StarBorder, Icons.Outlined.StarBorder)),
    TopDest(NearbyRoute, TabItem(R.string.tab_nearby, Icons.Outlined.NearMe, Icons.Outlined.NearMe)),
    TopDest(PlanRoute, TabItem(R.string.tab_plan, Icons.Outlined.Directions, Icons.Outlined.Directions)),
    TopDest(SearchRoute, TabItem(R.string.tab_search, Icons.Outlined.Search, Icons.Outlined.Search)),
)

@Composable
private fun MainScaffold() {
    val navController = rememberNavController()
    val navigator = remember(navController) { Navigator(navController) }
    val backStack by navController.currentBackStackEntryAsState()
    val dest = backStack?.destination
    val selectedIndex = topDestinations.indexOfFirst { top -> dest?.hierarchy?.any { it.hasRoute(top.route::class) } == true }

    fun selectTab(i: Int) {
        navController.navigate(topDestinations[i].route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalNavigator provides navigator) {
        BoxWithConstraints(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            if (maxWidth >= 600.dp) {
                // Tablets and unfolded foldables: a side rail.
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.background,
                        modifier = Modifier.fillMaxHeight().statusBarsPadding(),
                    ) {
                        Spacer(Modifier.weight(1f))
                        topDestinations.forEachIndexed { i, top ->
                            val selected = i == selectedIndex
                            NavigationRailItem(
                                selected = selected,
                                onClick = { selectTab(i) },
                                icon = { Icon(if (selected) top.item.selectedIcon else top.item.icon, contentDescription = stringResource(top.item.label)) },
                                label = { Text(stringResource(top.item.label)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = CtrlColors.OnAccent,
                                    selectedTextColor = CtrlColors.AccentText,
                                    indicatorColor = CtrlColors.Accent,
                                    unselectedIconColor = CtrlColors.Muted,
                                    unselectedTextColor = CtrlColors.Muted,
                                ),
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                    Box(Modifier.weight(1f).fillMaxHeight().navigationBarsPadding()) { AppNavHost(navController) }
                }
            } else {
                PhoneLayout(navController, showTabs = selectedIndex >= 0, selectedIndex = selectedIndex, onSelect = ::selectTab)
            }
        }
    }
}

/**
 * Phones: content runs edge to edge and scrolls under the floating Ctrl tab bar.
 * The bar shows on the main tabs; pushed screens get the full height.
 */
@Composable
private fun PhoneLayout(navController: NavHostController, showTabs: Boolean, selectedIndex: Int, onSelect: (Int) -> Unit) {
    val density = LocalDensity.current
    var overlayHeight by remember { mutableStateOf(0.dp) }

    CompositionLocalProvider(LocalBottomClearance provides if (showTabs) overlayHeight else 0.dp) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .then(if (showTabs) Modifier else Modifier.navigationBarsPadding()),
            ) {
                AppNavHost(navController)
            }
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { overlayHeight = with(density) { it.height.toDp() } }
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedVisibility(
                    visible = showTabs,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                ) {
                    CtrlBottomBar(
                        items = topDestinations.map { it.item },
                        selectedIndex = selectedIndex,
                        onSelect = onSelect,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = StopsRoute) {
        composable<StopsRoute> { StopsScreen() }
        composable<NearbyRoute> { NearbyScreen() }
        composable<SearchRoute> { SearchScreen() }
        composable<PlanRoute> { PlanScreen() }
        composable<PickPlaceRoute> { it.toRoute<PickPlaceRoute>().let { r -> PickPlaceScreen(r.forFrom, r.saveAs) } }
        composable<JourneyRoute> { JourneyScreen(it.toRoute<JourneyRoute>().index) }
        composable<GtfsKeyRoute> { com.vknxworks.lijnctrl.ui.live.GtfsKeyScreen() }
        composable<RouteMapRoute> { com.vknxworks.lijnctrl.ui.plan.RouteMapScreen(it.toRoute<RouteMapRoute>().index) }

        composable<StopRoute> { val r = it.toRoute<StopRoute>(); StopScreen(r.entity, r.number, r.name) }
        composable<TimetableRoute> { val r = it.toRoute<TimetableRoute>(); TimetableScreen(r.entity, r.number, r.name) }
        composable<LineRoute> { val r = it.toRoute<LineRoute>(); LineScreen(r.entity, r.line, r.direction) }
        composable<TripRoute> { val r = it.toRoute<TripRoute>(); TripScreen(r.entity, r.line, r.direction, r.trip, r.date, r.fromStop) }
        composable<TownRoute> { val r = it.toRoute<TownRoute>(); TownScreen(r.number, r.name) }
        composable<PlaceRoute> { val r = it.toRoute<PlaceRoute>(); PlaceScreen(r.latitude, r.longitude, r.name, r.detail) }
        composable<YouRoute> { YouScreen() }
        composable<SettingsRoute> { SettingsScreen() }
        composable<ApiKeyRoute> {
            val navigator = LocalNavigator.current
            ApiKeyScreen(firstRun = false, unreadable = false, onDone = navigator::back, onCancel = navigator::back)
        }
    }
}
