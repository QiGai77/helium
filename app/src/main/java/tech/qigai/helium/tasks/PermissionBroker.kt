package tech.qigai.helium.tasks

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import tech.qigai.helium.agent.tools.PermissionGate
import tech.qigai.helium.agent.tools.Perms
import java.lang.ref.WeakReference

/**
 * Lets a tool ask for a runtime permission mid-run. The system dialog needs a resumed Activity, so MainActivity
 * [attach]es itself; background runs (no Activity on screen) get a plain "not granted" and the tool explains.
 */
class PermissionBroker(private val context: Context) : PermissionGate {
    private var activity: WeakReference<ComponentActivity>? = null
    private var launcher: ActivityResultLauncher<Array<String>>? = null
    @Volatile private var pending: CompletableDeferred<Map<String, Boolean>>? = null
    private val mutex = Mutex()

    /** Call from Activity.onCreate (before STARTED). */
    fun attach(a: ComponentActivity) {
        val l = a.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { pending?.complete(it) }
        activity = WeakReference(a)
        launcher = l
        a.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (activity?.get() === a) { activity = null; launcher = null; pending?.complete(emptyMap()) }
            }
        })
    }

    /** True while MainActivity is resumed (an activity can be started directly, no background-launch limits). */
    fun foreground(): Boolean = activity?.get()?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) == true

    fun granted(permission: String): Boolean {
        if (permission == Perms.POST_NOTIFICATIONS && Build.VERSION.SDK_INT < 33) return true
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun ensure(permission: String): Boolean {
        if (granted(permission)) return true
        return mutex.withLock {
            if (granted(permission)) return@withLock true
            val a = activity?.get() ?: return@withLock false
            val l = launcher ?: return@withLock false
            if (!a.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return@withLock false
            val d = CompletableDeferred<Map<String, Boolean>>()
            pending = d
            withContext(Dispatchers.Main) { l.launch(arrayOf(permission)) }
            val r = withTimeoutOrNull(120_000) { d.await() }
            pending = null
            r?.get(permission) ?: granted(permission)
        }
    }
}
