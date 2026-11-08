package dev.jimmy.blog

import java.net.InetAddress

/** Numeric literals only: never resolve a header or configuration value through DNS. */
internal fun parseIpLiteral(value: String): InetAddress? {
    if (value.length !in 2..45) return null
    if (':' in value) {
        if (!value.all { it in "0123456789abcdefABCDEF:." }) return null
        return try {
            InetAddress.getByName(value)
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: java.net.UnknownHostException) {
            null
        }
    }
    val parts = value.split('.')
    if (parts.size != 4) return null
    val bytes = parts.map { part ->
        if (part.isEmpty() || part.length > 3 || !part.all { it in '0'..'9' } ||
            (part.length > 1 && part.startsWith('0'))
        ) return null
        (part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null).toByte()
    }.toByteArray()
    return InetAddress.getByAddress(bytes)
}

internal class ProxyNetwork private constructor(private val bytes: ByteArray, private val prefix: Int) {
    fun contains(address: InetAddress): Boolean {
        val candidate = address.address
        if (candidate.size != bytes.size) return false
        for (index in bytes.indices) {
            val bits = (prefix - index * 8).coerceIn(0, 8)
            val mask = (0xff shl (8 - bits)) and 0xff
            if ((bytes[index].toInt() and mask) != (candidate[index].toInt() and mask)) return false
        }
        return true
    }

    companion object {
        fun parse(value: String): ProxyNetwork {
            val invalid = "LOGIN_TRUSTED_PROXY_CIDRS must contain numeric IPv4/IPv6 CIDRs; /0 is not allowed"
            val parts = value.split('/')
            require(parts.size == 2) { invalid }
            val address = parseIpLiteral(parts[0])
            val prefix = parts[1].takeIf { it.isNotEmpty() && it.all { char -> char in '0'..'9' } }?.toIntOrNull()
            require(address != null && prefix != null && prefix in 1..address.address.size * 8) { invalid }
            return ProxyNetwork(address.address, prefix)
        }
    }
}

internal class LoginClientResolver(private val trustedProxies: List<ProxyNetwork>) {
    private fun isTrusted(address: InetAddress) = trustedProxies.any { it.contains(address) }

    fun resolve(transportPeer: String, forwardedFor: List<String>?): String? {
        var current = parseIpLiteral(transportPeer) ?: return "unresolved-peer"
        if (!isTrusted(current)) return current.hostAddress

        // Invalid trusted-proxy metadata must not provide a second bucket to bypass an existing block.
        if (forwardedFor.isNullOrEmpty() || forwardedFor.size > 32 || forwardedFor.sumOf { it.length.toLong() } > 4096) {
            return null
        }
        val chain = forwardedFor.flatMap { it.split(',') }
        if (chain.size > 32) return null
        for (entry in chain.asReversed()) {
            if (!isTrusted(current)) return current.hostAddress
            current = parseIpLiteral(entry.trim()) ?: return null
        }
        // A chain consisting entirely of trusted proxies has no verified client.
        return if (isTrusted(current)) null else current.hostAddress
    }
}
