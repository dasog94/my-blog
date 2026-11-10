package dev.jimmy.blog

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

internal class LoginTestClock(@Volatile var nanos: Long = 0) {
    fun advanceMillis(millis: Long) { nanos += TimeUnit.MILLISECONDS.toNanos(millis) }
}

class LoginAttemptLimiterTest {
    @Test
    fun `rolling windows recover exactly when the oldest slot expires without extending on rejection`() {
        val clock = LoginTestClock()
        val limiter = LoginAttemptLimiter(LoginRateLimitConfig(clientAttempts = 2), { clock.nanos })
        assertNull(limiter.retryAfterOrAcquire("client"))
        clock.advanceMillis(10_000)
        assertNull(limiter.retryAfterOrAcquire("client"))
        assertEquals(50L, limiter.retryAfterOrAcquire("client"))
        clock.advanceMillis(49_999)
        assertEquals(1L, limiter.retryAfterOrAcquire("client"))
        clock.advanceMillis(1)
        assertNull(limiter.retryAfterOrAcquire("client"))
        // Only the first slot expired. This is not a fixed-window reset of the whole bucket.
        assertEquals(10L, limiter.retryAfterOrAcquire("client"))
    }

    @Test
    fun `client budgets are independent and rejected attempts do not consume global slots`() {
        val limiter = LoginAttemptLimiter(LoginRateLimitConfig(clientAttempts = 1, totalAttempts = 3), { 0L })
        assertNull(limiter.retryAfterOrAcquire("one"))
        repeat(10) { assertEquals(60L, limiter.retryAfterOrAcquire("one")) }
        assertNull(limiter.retryAfterOrAcquire("two"))
        assertNull(limiter.retryAfterOrAcquire("three"))
        assertEquals(60L, limiter.retryAfterOrAcquire("four"))
    }

    @Test
    fun `rotating client keys cannot evade the aggregate budget and expired clients release capacity`() {
        val clock = LoginTestClock()
        val limiter = LoginAttemptLimiter(LoginRateLimitConfig(clientAttempts = 2, totalAttempts = 3), { clock.nanos })
        repeat(3) { assertNull(limiter.retryAfterOrAcquire("client-$it")) }
        repeat(1000) { assertEquals(60L, limiter.retryAfterOrAcquire("new-client-$it")) }
        clock.advanceMillis(60_000)
        repeat(3) { assertNull(limiter.retryAfterOrAcquire("recovered-$it")) }
        assertEquals(60L, limiter.retryAfterOrAcquire("another"))
    }

    @Test
    fun `atomic admission restricts concurrent requests from one client and many clients`() {
        val executor = Executors.newFixedThreadPool(16)
        try {
            val perClient = LoginAttemptLimiter(LoginRateLimitConfig(), { 0L })
            val sameClient = executor.invokeAll(List(100) { Callable { perClient.retryAfterOrAcquire("same") } })
            assertEquals(5, sameClient.count { it.get() == null })
            val aggregate = LoginAttemptLimiter(LoginRateLimitConfig(totalAttempts = 20), { 0L })
            val differentClients = executor.invokeAll(List(100) { index ->
                Callable { aggregate.retryAfterOrAcquire("client-$index") }
            })
            assertEquals(20, differentClients.count { it.get() == null })
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `monotonic clock subtraction handles a nanoTime wraparound`() {
        val clock = LoginTestClock(Long.MAX_VALUE - TimeUnit.SECONDS.toNanos(30))
        val limiter = LoginAttemptLimiter(LoginRateLimitConfig(clientAttempts = 1), { clock.nanos })
        assertNull(limiter.retryAfterOrAcquire("client"))
        clock.advanceMillis(59_999)
        assertEquals(1L, limiter.retryAfterOrAcquire("client"))
        clock.advanceMillis(1)
        assertNull(limiter.retryAfterOrAcquire("client"))
    }

    @Test
    fun `environment policy has safe defaults and accepts explicit bounded values`() {
        val defaults = LoginRateLimitConfig.fromEnvironment(emptyMap())
        assertEquals(5, defaults.clientAttempts)
        assertEquals(100, defaults.totalAttempts)
        assertEquals(60L, defaults.windowSeconds)
        assertEquals(emptyList(), defaults.trustedProxies)
        val explicit = LoginRateLimitConfig.fromEnvironment(mapOf(
            "LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS" to "2",
            "LOGIN_RATE_LIMIT_TOTAL_ATTEMPTS" to "10",
            "LOGIN_RATE_LIMIT_WINDOW_SECONDS" to "120",
            "LOGIN_TRUSTED_PROXY_CIDRS" to "192.0.2.5/32,2001:db8::/48",
        ))
        assertEquals(2, explicit.clientAttempts)
        assertEquals(10, explicit.totalAttempts)
        assertEquals(120L, explicit.windowSeconds)
        assertEquals(2, explicit.trustedProxies.size)
    }

    @Test
    fun `bad environment values fail startup instead of silently disabling throttling`() {
        for (name in listOf("LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS", "LOGIN_RATE_LIMIT_TOTAL_ATTEMPTS",
            "LOGIN_RATE_LIMIT_WINDOW_SECONDS")) {
            for (value in listOf("", " ", "0", "-1", "1.5", "NaN", "9223372036854775808")) {
                assertFailsWith<IllegalArgumentException>("$name=$value") {
                    LoginRateLimitConfig.fromEnvironment(mapOf(name to value))
                }
            }
        }
        for (env in listOf(
            mapOf("LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS" to "101"),
            mapOf("LOGIN_RATE_LIMIT_TOTAL_ATTEMPTS" to "10001"),
            mapOf("LOGIN_RATE_LIMIT_WINDOW_SECONDS" to "3601"),
        )) {
            assertFailsWith<IllegalArgumentException> { LoginRateLimitConfig.fromEnvironment(env) }
        }
    }
}
