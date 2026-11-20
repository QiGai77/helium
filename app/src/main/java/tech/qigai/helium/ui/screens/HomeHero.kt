package tech.qigai.helium.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import tech.qigai.helium.HeliumApplication
import tech.qigai.helium.data.model.HeroEvent

/**
 * Home hero state: asks once for coarse location + calendar, refreshes stale weather, then shows the nearest
 * calendar event (re-checked every minute while Home is visible) or, without one, today's weather.
 */
@Composable
fun rememberHomeHero(): HeroEvent? {
    val app = LocalContext.current.applicationContext as HeliumApplication
    val repo = app.glance
    var hero by remember { mutableStateOf<HeroEvent?>(null) }
    var granted by remember { mutableIntStateOf(0) }
    val weather by repo.weather.collectAsState(initial = null)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted++ }

    LaunchedEffect(Unit) {
        if (!repo.permissionsAsked) {
            repo.permissionsAsked = true
            launcher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.READ_CALENDAR))
        }
    }
    LaunchedEffect(granted) {
        repo.refreshWeather(force = granted > 0, useLocation = true)
    }
    LaunchedEffect(granted, weather?.fetchedAt) {
        while (true) {
            hero = runCatching { repo.hero() }.getOrNull()
            delay(60_000)
        }
    }
    return hero
}
