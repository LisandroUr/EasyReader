package com.example.easyreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.easyreader.ui.HomeScreen
import com.example.easyreader.ui.LibraryViewModel
import com.example.easyreader.ui.PdfViewerScreen
import com.example.easyreader.ui.SeriesDetailScreen
import com.example.easyreader.ui.SettingsScreen
import com.example.easyreader.ui.theme.EasyReaderTheme
import com.example.easyreader.worker.LibrarySyncWorker
import java.util.concurrent.TimeUnit

import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: LibraryViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val dynamicColor by viewModel.dynamicColor.collectAsStateWithLifecycle()

            EasyReaderTheme(
                themeMode = themeMode,
                dynamicColor = dynamicColor
            ) {
                EasyReaderApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun EasyReaderApp(viewModel: LibraryViewModel = viewModel()) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                viewModel = viewModel,
                onSeriesClick = { seriesId ->
                    navController.navigate("series_detail/$seriesId")
                },
                onBookClick = { bookId ->
                    navController.navigate("viewer/$bookId")
                },
                onSettingsClick = {
                    navController.navigate("settings")
                }
            )
        }
        composable(
            route = "series_detail/{seriesId}",
            arguments = listOf(navArgument("seriesId") { type = NavType.LongType })
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getLong("seriesId") ?: return@composable
            SeriesDetailScreen(
                seriesId = seriesId,
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onBookClick = { bookId ->
                    navController.navigate("viewer/$bookId")
                }
            )
        }
        composable(
            route = "viewer/{bookId}",
            arguments = listOf(navArgument("bookId") { type = NavType.LongType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getLong("bookId") ?: return@composable
            PdfViewerScreen(
                bookId = bookId,
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onNextBookClick = { nextBookId ->
                    navController.navigate("viewer/$nextBookId")
                }
            )
        }
        composable("settings") {
            SettingsScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() }
            )
        }
    }
}
