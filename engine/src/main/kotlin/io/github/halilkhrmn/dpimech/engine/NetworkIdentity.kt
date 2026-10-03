package io.github.halilkhrmn.dpimech.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import io.github.halilkhrmn.dpimech.core.IspInfo
import io.github.halilkhrmn.dpimech.core.IspLookup
import io.github.halilkhrmn.dpimech.core.NetworkInfo
import io.github.halilkhrmn.dpimech.core.Transport
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Watches DPIMech's default network (the real Wi-Fi or mobile network: DPIMech itself is outside
 * its VPN) for as long as the app process lives. On every new network it publishes the transport
 * and, on mobile data, the operator at once; then, when [configure] allows it, it asks ipwho.is
 * for the provider over that network. The VPN service follows [flow] for the per-network
 * strategy memory.
 */
object NetworkIdentity {
    private val state = MutableStateFlow<NetworkInfo?>(null)
    val flow: StateFlow<NetworkInfo?> = state.asStateFlow()

    @Volatile
    private var current: Network? = null
    private var started = false

    /** Look the provider up on every new network (automatic strategy). Off until [configure]. */
    @Volatile
    private var autoLookup = false

    /** Any lookup at all, including [refresh] for a test the user started. */
    @Volatile
    private var lookupAllowed = false

    /**
     * What may go to ipwho.is (it sees the phone's IP address). Turning [auto] on looks the
     * current network up at once.
     */
    fun configure(auto: Boolean, allowed: Boolean) {
        val wasAuto = autoLookup
        lookupAllowed = allowed
        autoLookup = auto && allowed
        val network = current
        if (autoLookup && !wasAuto && network != null && state.value?.isp == null) lookupInBackground(network)
    }

    private fun lookupInBackground(network: Network) {
        thread(name = "isp-lookup", isDaemon = true) {
            val isp = lookup(network) ?: return@thread
            if (current != network) return@thread
            val info = state.value?.copy(isp = isp) ?: return@thread
            state.value = info
            EngineLog.add("network: ${describe(info)} (${isp.networkKey}, ${isp.country})")
        }
    }

    @Synchronized
    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val cm = app.getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return
                val transport = when {
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> Transport.WIFI
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> Transport.CELLULAR
                    caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> Transport.ETHERNET
                    else -> Transport.OTHER
                }
                // Capabilities change often (signal, validation); act once per network.
                if (network == current && state.value?.transport == transport) return
                current = network
                val base = (if (transport == Transport.CELLULAR) mobile(app) else NetworkInfo(transport))
                    .copy(hasIpv6 = ipv6(cm, network))
                state.value = base
                EngineLog.add("network: ${describe(base)}")
                if (autoLookup) lookupInBackground(network)
            }

            // IPv6 addresses often arrive a moment after the network itself.
            override fun onLinkPropertiesChanged(network: Network, lp: LinkProperties) {
                if (network != current) return
                val v6 = NetworkInfo.hasGlobalIpv6(lp.linkAddresses.map { it.address })
                val now = state.value ?: return
                if (now.hasIpv6 != v6) {
                    state.value = now.copy(hasIpv6 = v6)
                    EngineLog.add("network: IPv6 ${if (v6) "available" else "not available"}")
                }
            }

            override fun onLost(network: Network) {
                if (network != current) return
                current = null
                state.value = null
                EngineLog.add("network: none")
            }
        }
        runCatching { cm.registerDefaultNetworkCallback(callback) }
            .onFailure { EngineLog.add("network callback failed: $it") }
    }

    /**
     * Looks the provider up again on the current network (Strategy Lab's "provider" line), when
     * the user allows lookups at all.
     */
    fun refresh(): IspInfo? {
        if (!lookupAllowed) return null
        val network = current
        val isp = lookup(network) ?: return null
        state.value?.takeIf { network == current }?.let { state.value = it.copy(isp = isp) }
        return isp
    }

    /** Blocking: asks ipwho.is, over [network] when given. Null when offline or refused. */
    fun lookup(network: Network? = null): IspInfo? = runCatching {
        val url = URL(IspLookup.URL)
        val conn = (network?.openConnection(url) ?: url.openConnection()) as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        try {
            IspLookup.parse(conn.inputStream.use { it.readBytes().decodeToString() }).getOrThrow()
        } finally {
            conn.disconnect()
        }
    }.onFailure { EngineLog.add("provider lookup failed: $it") }.getOrNull()

    private fun ipv6(cm: ConnectivityManager, network: Network): Boolean? =
        cm.getLinkProperties(network)?.let { lp -> NetworkInfo.hasGlobalIpv6(lp.linkAddresses.map { it.address }) }

    /** Operator code and name from Android; both are readable without a permission. */
    private fun mobile(context: Context): NetworkInfo {
        val tm = context.getSystemService(TelephonyManager::class.java)
        return NetworkInfo(
            Transport.CELLULAR,
            mccMnc = runCatching { tm?.networkOperator }.getOrNull()?.ifEmpty { null },
            operatorName = runCatching { tm?.networkOperatorName }.getOrNull()?.ifEmpty { null },
        )
    }

    private fun describe(n: NetworkInfo): String {
        val kind = when (n.transport) {
            Transport.WIFI -> "Wi-Fi"
            Transport.CELLULAR -> "mobile data"
            Transport.ETHERNET -> "Ethernet"
            Transport.OTHER -> "other"
        }
        val v6 = when (n.hasIpv6) {
            true -> "IPv6"
            false -> "no IPv6"
            null -> null
        }
        return listOfNotNull(kind, n.providerName, n.mccMnc, v6).joinToString(", ")
    }
}
