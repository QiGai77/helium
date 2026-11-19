package tech.qigai.helium

import android.app.Application
import tech.qigai.helium.data.settings.SettingsRepository

class HeliumApplication : Application() {
    val settings: SettingsRepository by lazy { SettingsRepository(this) }
}
