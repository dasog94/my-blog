package dev.jimmy.blog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoginClientResolverTest {
    private val trusted = listOf(ProxyNetwork.parse("192.0.2.0/24"), ProxyNetwork.parse("2001:db8:ffff::/48"))

    @Test
    fun `default and untrusted peers ignore all forwarded address claims`() {
        for (resolver in listOf(LoginClientResolver(emptyList()), LoginClientResolver(trusted))) {
            assertEquals("198.51.100.10", resolver.resolve("198.51.100.10", listOf("203.0.113.1")))
            assertEquals("198.51.100.10", resolver.resolve("198.51.100.10", listOf("invalid", "203.0.113.2")))
        }
    }

    @Test
    fun `verified proxy chain is walked from the socket to the first untrusted hop`() {
        val resolver = LoginClientResolver(trusted)
        assertEquals("198.51.100.10", resolver.resolve("192.0.2.5", listOf("198.51.100.10")))
        // A forged left prefix, including invalid input, cannot change the selected address.
        assertEquals("198.51.100.10", resolver.resolve("192.0.2.5",
            listOf("malformed, 203.0.113.1, 198.51.100.10, 192.0.2.8")))
        assertEquals("198.51.100.10", resolver.resolve("192.0.2.5",
            listOf("203.0.113.1, 198.51.100.10", "192.0.2.8")))
        // An untrusted intermediate proxy is the boundary, regardless of the claimed end user.
        assertEquals("198.51.100.20", resolver.resolve("192.0.2.5",
            listOf("203.0.113.1, 198.51.100.20, 192.0.2.8")))
    }

    @Test
    fun `missing malformed excessive and entirely trusted chains cannot identify a client`() {
        val resolver = LoginClientResolver(trusted)
        val invalid = listOf(null, emptyList(), listOf(""), listOf("unknown"), listOf("example.test"),
            listOf("198.51.100.1:8080"), listOf("198.51.100.1,"), listOf("192.0.2.8"),
            listOf("192.0.2.8, not-an-ip"), listOf("a".repeat(4097)), List(33) { "192.0.2.8" })
        for (headers in invalid) {
            assertNull(resolver.resolve("192.0.2.5", headers), headers.toString())
        }
        assertEquals("unresolved-peer", resolver.resolve("unparseable-peer", listOf("203.0.113.1")))
    }

    @Test
    fun `IPv6 equivalents and IPv4 mapped IPv6 share canonical keys`() {
        val resolver = LoginClientResolver(emptyList())
        assertEquals(resolver.resolve("2001:db8::1", null),
            resolver.resolve("2001:0DB8:0:0:0:0:0:1", null))
        assertEquals("198.51.100.10", resolver.resolve("::ffff:198.51.100.10", null))
        val throughIpv6Proxy = LoginClientResolver(trusted)
        assertEquals(resolver.resolve("2001:db8:1::2", null),
            throughIpv6Proxy.resolve("2001:db8:ffff::1", listOf("2001:db8:1::2")))
        assertTrue(ProxyNetwork.parse("2001:db8:ffff::/49").contains(parseIpLiteral("2001:db8:ffff:7fff::1")!!))
        assertEquals(false, ProxyNetwork.parse("2001:db8:ffff::/49")
            .contains(parseIpLiteral("2001:db8:ffff:8000::1")!!))
    }

    @Test
    fun `IP parsing never accepts names ports scopes or ambiguous IPv4 forms`() {
        for (value in listOf("localhost", "example.test", "2130706433", "127.1", "0x7f000001",
            "127.000.0.1", "1.2.3.256", "1.2.3.-1", "[2001:db8::1]", "fe80::1%eth0", "1.2.3.4:80")) {
            assertNull(parseIpLiteral(value), value)
        }
    }

    @Test
    fun `proxy trust requires explicit valid CIDRs and rejects trust all`() {
        for (value in listOf("", "*", "0.0.0.0/0", "::/0", "example.test/32", "192.0.2.1",
            "192.0.2.1/33", "2001:db8::/129", "192.0.2.1/-1", "192.0.2.1/+32", "192.0.2.1/32,",
            "192.0.2.1/32,,2001:db8::/32")) {
            assertFailsWith<IllegalArgumentException>(value) {
                LoginRateLimitConfig.fromEnvironment(mapOf("LOGIN_TRUSTED_PROXY_CIDRS" to value))
            }
        }
    }
}
