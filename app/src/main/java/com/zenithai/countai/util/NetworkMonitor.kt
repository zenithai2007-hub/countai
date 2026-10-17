package com.zenithai.countai.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Simple online/offline check used for the OFFLINE MODE badge. */
object NetworkMonitor {

    fun isOnline(context: Context): Boolean = try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } catch (e: Exception) {
        true // If we can't tell, don't claim offline.
    }
}
