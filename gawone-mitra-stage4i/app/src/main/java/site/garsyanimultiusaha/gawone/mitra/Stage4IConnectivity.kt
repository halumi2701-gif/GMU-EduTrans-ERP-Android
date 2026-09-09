package site.garsyanimultiusaha.gawone.mitra

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

internal object Stage4INetworkRecovery {
    val onlineAgain = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
}

internal class Stage4IConnectivityMonitor(context: Context) : AutoCloseable {
    private val cm = context.applicationContext
        .getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val mutableOnline = MutableStateFlow(isCurrentlyOnline())
    val online: StateFlow<Boolean> = mutableOnline

    private var lastOnline = mutableOnline.value

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = update()
        override fun onLost(network: Network) = update()
        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) = update()
    }

    init {
        cm.registerDefaultNetworkCallback(callback)
    }

    private fun update() {
        val now = isCurrentlyOnline()
        mutableOnline.value = now
        if (!lastOnline && now) {
            Stage4INetworkRecovery.onlineAgain.tryEmit(Unit)
        }
        lastOnline = now
    }

    private fun isCurrentlyOnline(): Boolean {
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    override fun close() {
        runCatching { cm.unregisterNetworkCallback(callback) }
    }
}
