package de.xitee.torchbrightness.curve

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import de.xitee.torchbrightness.BuildConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

/** How the app can read `dumpsys display`. */
enum class CurveAccess {
    /** DUMP permission granted via adb. */
    DUMP_PERMISSION,
    /** Shizuku running and permission granted. */
    SHIZUKU,
    /** Shizuku running, permission not granted yet. */
    SHIZUKU_NO_PERMISSION,
    NONE,
}

/** Reads the brightness curve, preferring the DUMP permission and falling back to Shizuku. */
class CurveReader(private val context: Context) {

    private val _access = MutableStateFlow(computeAccess())
    val access: StateFlow<CurveAccess> = _access.asStateFlow()

    private var dumpService: IDumpService? = null

    private val shizukuArgs = Shizuku.UserServiceArgs(
        ComponentName(BuildConfig.APPLICATION_ID, DumpService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("dump")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)

    private val binderListener = Shizuku.OnBinderReceivedListener { refreshAccess() }
    private val deadListener = Shizuku.OnBinderDeadListener {
        dumpService = null
        refreshAccess()
    }
    private val permissionListener = Shizuku.OnRequestPermissionResultListener { _, _ -> refreshAccess() }

    fun start() {
        Shizuku.addBinderReceivedListenerSticky(binderListener)
        Shizuku.addBinderDeadListener(deadListener)
        Shizuku.addRequestPermissionResultListener(permissionListener)
    }

    fun stop() {
        Shizuku.removeBinderReceivedListener(binderListener)
        Shizuku.removeBinderDeadListener(deadListener)
        Shizuku.removeRequestPermissionResultListener(permissionListener)
    }

    fun refreshAccess() {
        _access.value = computeAccess()
    }

    fun requestShizukuPermission() {
        if (Shizuku.pingBinder() && !Shizuku.isPreV11()) Shizuku.requestPermission(REQUEST_CODE)
    }

    /** Reads and parses the curve. Throws if no access or the dump cannot be read. */
    suspend fun read(): DumpParser.Result {
        refreshAccess()
        val text = when (access.value) {
            CurveAccess.DUMP_PERMISSION -> withContext(Dispatchers.IO) { runDumpsysDisplay() }
            CurveAccess.SHIZUKU -> withContext(Dispatchers.IO) { shizukuService().dumpDisplay() }
            CurveAccess.SHIZUKU_NO_PERMISSION, CurveAccess.NONE -> error("No access to dumpsys")
        }
        return DumpParser.parse(text, System.currentTimeMillis())
    }

    private suspend fun shizukuService(): IDumpService {
        dumpService?.takeIf { it.asBinder().pingBinder() }?.let { return it }
        val deferred = CompletableDeferred<IDumpService>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null && binder.pingBinder()) {
                    deferred.complete(IDumpService.Stub.asInterface(binder))
                } else {
                    deferred.completeExceptionally(IllegalStateException("Invalid Shizuku service"))
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                dumpService = null
            }
        }
        withContext(Dispatchers.Main) { Shizuku.bindUserService(shizukuArgs, connection) }
        return withTimeout(SHIZUKU_TIMEOUT_MS) { deferred.await() }.also { dumpService = it }
    }

    private fun computeAccess(): CurveAccess = when {
        context.checkSelfPermission(Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED ->
            CurveAccess.DUMP_PERMISSION
        !Shizuku.pingBinder() || Shizuku.isPreV11() -> CurveAccess.NONE
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> CurveAccess.SHIZUKU
        else -> CurveAccess.SHIZUKU_NO_PERMISSION
    }

    private companion object {
        const val REQUEST_CODE = 4712
        const val SHIZUKU_TIMEOUT_MS = 5000L
    }
}
