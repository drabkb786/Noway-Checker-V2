package com.noway.responsechecker.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager

object NetworkInspector {
    fun inspect(context: Context): NetworkInfo {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork
        val caps = network?.let { cm.getNetworkCapabilities(it) }
        val links = network?.let { cm.getLinkProperties(it) }
        val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val transport = when {
            vpn -> "VPN"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Mobile data"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) == true -> "Bluetooth"
            else -> "Unknown"
        }
        val carrier = if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true) {
            runCatching {
                val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
                tm.networkOperatorName?.takeIf { it.isNotBlank() }
            }.getOrNull()
        } else null
        val proxy = links?.httpProxy?.let { p ->
            buildString {
                append(p.host)
                if (p.port > 0) append(":${p.port}")
            }
        }
        return NetworkInfo(
            transport = transport,
            carrier = carrier,
            metered = runCatching { cm.isActiveNetworkMetered }.getOrNull(),
            validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            vpn = vpn,
            dnsServers = links?.dnsServers?.mapNotNull { it.hostAddress }?.distinct().orEmpty(),
            systemProxy = proxy
        )
    }
}
