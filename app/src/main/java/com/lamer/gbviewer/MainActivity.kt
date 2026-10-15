package com.lamer.gbviewer
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import kotlin.random.Random
import com.lamer.gbviewer.ui.FavoriteViewerScreen
import com.lamer.gbviewer.ui.FavoritesScreen
import com.lamer.gbviewer.ui.FullscreenScreen
import com.lamer.gbviewer.ui.GBTheme
import com.lamer.gbviewer.ui.LockScreen
import com.lamer.gbviewer.ui.PostScreen
import com.lamer.gbviewer.ui.SearchScreen
import com.lamer.gbviewer.ui.SearchViewModel
import com.lamer.gbviewer.ui.SettingsScreen

class MainActivity : FragmentActivity() {
    private val gb get() = application as GBApp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Скрываем превью приложения в списке недавних.
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(false)
        setContent {
            val mode by gb.settings.theme.collectAsState()
            val lang by gb.settings.language.collectAsState()
            val accent by gb.settings.accentColor.collectAsState()
            val localized = remember(lang) {
                LocalizedContext(this@MainActivity, localizedResources(this@MainActivity, lang))
            }
            // Строки интерфейса берутся на выбранном языке (по умолчанию английский).
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
            ) {
                GBTheme(mode, accent) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppRoot()
                    }
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Пока окно не на переднем плане, содержимое скрыто в списке недавних.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onResume() {
        super.onResume()
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStop() {
        super.onStop()
        gb.lock.onAppStopped()
    }
}

private const val ROUTE_SEARCH = "search?tag={tag}"
private const val ROUTE_FAVORITES = "favorites"
private const val ROUTE_SETTINGS = "settings"

private data class Tab(val route: String, @StringRes val labelRes: Int, val icon: ImageVector, val matchPrefix: String)

private val tabs = listOf(
    Tab("search", R.string.tab_search, Icons.Filled.Search, "search"),
    Tab(ROUTE_FAVORITES, R.string.tab_favorites, Icons.Filled.Favorite, "favorites"),
    Tab(ROUTE_SETTINGS, R.string.tab_settings, Icons.Filled.Settings, "settings"),
)

@Composable
fun AppRoot() {
    val gb = LocalContext.current.app
    val locked by gb.lock.locked.collectAsState()
    val nav = rememberNavController()
    val backEntry by nav.currentBackStackEntryAsState()
    val route = backEntry?.destination?.route
    val showBar = route == ROUTE_SEARCH || route == ROUTE_FAVORITES || route == ROUTE_SETTINGS

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                if (showBar) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        tabs.forEach { tab ->
                            NavigationBarItem(
                                selected = route?.startsWith(tab.matchPrefix) == true,
                                onClick = {
                                    if (tab.route == ROUTE_FAVORITES && route != ROUTE_FAVORITES &&
                                        gb.settings.alwaysShuffleFavorites.value
                                    ) {
                                        gb.favoritesShuffleSeed.value = Random.nextLong()
                                    }
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = stringResource(tab.labelRes)) },
                                label = { Text(stringResource(tab.labelRes)) },
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = ROUTE_SEARCH,
                modifier = Modifier.padding(padding),
            ) {
                composable(
                    ROUTE_SEARCH,
                    arguments = listOf(navArgument("tag") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }),
                ) {
                    SearchScreen(onOpenPost = { id -> nav.navigate("post/$id") })
                }
                composable(
                    "post/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    val id = entry.arguments?.getLong("id") ?: 0L
                    // Список результатов поиска, из которого открыт пост (для листания свайпом).
                    val searchEntry = remember(entry) { nav.previousBackStackEntry }
                    val searchVm: SearchViewModel? =
                        if (searchEntry != null && searchEntry.destination.route == ROUTE_SEARCH) {
                            viewModel<SearchViewModel>(searchEntry)
                        } else {
                            null
                        }
                    PostScreen(
                        startId = id,
                        searchVm = searchVm,
                        onBack = { nav.popBackStack() },
                        onTagClick = { tag -> nav.navigate("search?tag=${Uri.encode(tag)}") },
                        onOpenFull = { postId -> nav.navigate("full/$postId") },
                    )
                }
                composable(
                    "full/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    FullscreenScreen(
                        postId = entry.arguments?.getLong("id") ?: 0L,
                        onBack = { nav.popBackStack() },
                    )
                }
                composable(ROUTE_FAVORITES) {
                    FavoritesScreen(onOpen = { id -> nav.navigate("favviewer/$id") })
                }
                composable(
                    "favviewer/{id}",
                    arguments = listOf(navArgument("id") { type = NavType.LongType }),
                ) { entry ->
                    FavoriteViewerScreen(
                        startId = entry.arguments?.getLong("id") ?: 0L,
                        onBack = { nav.popBackStack() },
                        onTagClick = { tag -> nav.navigate("search?tag=${Uri.encode(tag)}") },
                    )
                }
                composable(ROUTE_SETTINGS) { SettingsScreen() }
            }
        }

        // Экран блокировки поверх всего приложения.
        if (locked) LockScreen(gb.lock)
    }
}
