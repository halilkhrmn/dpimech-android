package io.github.halilkhrmn.dpimech.engine

import android.net.Network
import io.github.halilkhrmn.dpimech.core.IspInfo
import io.github.halilkhrmn.dpimech.core.IspLookup
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The provider of the network DPIMech's own traffic uses (DPIMech is outside its VPN, so this
 * is the real Wi-Fi or mobile network). Keys the per-network strategy memory.
 */
object NetworkIdentity {
    private val state = MutableStateFlow<IspInfo?>(null)
    val flow: StateFlow<IspInfo?> = state.asStateFlow()

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
    }.onFailure { EngineLog.add("ISP lookup failed: $it") }
        .getOrNull()
        ?.also { state.value = it }
}
