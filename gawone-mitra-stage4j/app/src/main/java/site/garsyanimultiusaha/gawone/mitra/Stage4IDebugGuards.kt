package site.garsyanimultiusaha.gawone.mitra

import android.os.StrictMode
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

internal object Stage4IDebugGuards {
    private val installed = AtomicBoolean(false)

    fun install() {
        if (!BuildConfig.DEBUG || !installed.compareAndSet(false, true)) return

        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectNetwork()
                .detectDiskReads()
                .detectDiskWrites()
                .penaltyLog()
                .build()
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder()
                .detectLeakedClosableObjects()
                .detectActivityLeaks()
                .penaltyLog()
                .build()
        )
    }
}

internal object Stage4IStartupTrace {
    private val startElapsedMs = SystemClock.elapsedRealtime()
    private val marked = AtomicBoolean(false)

    fun markRuntimeReady() {
        if (!BuildConfig.DEBUG || !marked.compareAndSet(false, true)) return
        val elapsed = SystemClock.elapsedRealtime() - startElapsedMs
        Log.i("GAWONE_STARTUP", "runtime_ready_ms=" + elapsed)
    }
}
