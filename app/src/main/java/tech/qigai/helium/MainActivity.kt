package tech.qigai.helium

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import tech.qigai.helium.data.settings.ThemeMode
import tech.qigai.helium.ui.nav.HeliumNavHost
import tech.qigai.helium.ui.theme.HeliumTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val app = application as HeliumApplication
        app.permissions.attach(this)
        routeFrom(intent)
        setContent {
            val mode by app.settings.themeMode.collectAsState()
            val darkTheme = when (mode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) {
                    SystemBarStyle.dark(Color.TRANSPARENT)
                } else {
                    SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            HeliumTheme(darkTheme = darkTheme) {
                HeliumNavHost()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        routeFrom(intent)
    }

    /** Notifications open a specific page (a task, the approval queue). */
    private fun routeFrom(intent: Intent?) {
        val route = intent?.getStringExtra(tech.qigai.helium.tasks.HeliumNotifications.ROUTE_EXTRA) ?: return
        (application as HeliumApplication).pendingRoute.value = route
        intent.removeExtra(tech.qigai.helium.tasks.HeliumNotifications.ROUTE_EXTRA)
    }
}
