package com.tatsu.homehub.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities

class NetworkMonitor(
    context: Context,
    private val onAvailable: () -> Unit
) {
    private val manager =
        context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            onAvailable()
        }
    }

    fun start() {
        runCatching {
            manager.registerDefaultNetworkCallback(callback)
        }
    }

    fun stop() {
        runCatching {
            manager.unregisterNetworkCallback(callback)
        }
    }

    fun isOnline(): Boolean {
        val network = manager.activeNetwork ?: return false
        val caps = manager.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}
