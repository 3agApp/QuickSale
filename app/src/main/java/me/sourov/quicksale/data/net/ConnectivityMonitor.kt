package me.sourov.quicksale.data.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Whether this device currently has a usable network.
 *
 * The app had no idea before: a sync or a placement simply failed at the moment it was attempted
 * and nothing ever noticed the network coming back. The top bar uses this to say so out loud —
 * orders go straight to the store, so being offline is the difference between an order that works
 * and one that won't — and the order list uses it to refetch once there is something to fetch.
 *
 * "Usable" is [NetworkCapabilities.NET_CAPABILITY_VALIDATED], not merely connected: a fair's guest
 * wi-fi that has associated but not yet let you past its login page is exactly the case that would
 * otherwise look online and refuse every request.
 */
class ConnectivityMonitor(context: Context) {

    private val manager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    val online: Flow<Boolean> = callbackFlow {
        val manager = manager
        if (manager == null) {
            // No ConnectivityManager at all is not a device we can reason about; assume online and
            // let requests fail honestly rather than blocking every send behind a false negative.
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }

        fun publish() = trySend(manager.isValidated())

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { publish() }
            override fun onLost(network: Network) { publish() }
            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities,
            ) {
                publish()
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        manager.registerNetworkCallback(request, callback)
        publish()

        awaitClose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }.distinctUntilChanged()

    private fun ConnectivityManager.isValidated(): Boolean {
        val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
