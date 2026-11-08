package dev.jimmy.blog

import java.util.ArrayDeque
import java.util.concurrent.TimeUnit

internal data class LoginRateLimitConfig(
    val clientAttempts: Int = 5,
    val totalAttempts: Int = 100,
    val windowSeconds: Long = 60,
    val trustedProxies: List<ProxyNetwork> = emptyList(),
) {
    init {
        require(totalAttempts in 1..10_000) { "LOGIN_RATE_LIMIT_TOTAL_ATTEMPTS must be between 1 and 10000" }
        require(clientAttempts in 1..totalAttempts) {
            "LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS must be positive and at most LOGIN_RATE_LIMIT_TOTAL_ATTEMPTS"
        }
        require(windowSeconds in 1..3600) { "LOGIN_RATE_LIMIT_WINDOW_SECONDS must be between 1 and 3600" }
    }

    companion object {
        fun fromEnvironment(env: Map<String, String>): LoginRateLimitConfig {
            fun number(name: String, fallback: Long): Long = env[name]?.let {
                it.trim().toLongOrNull() ?: throw IllegalArgumentException("$name must be an integer")
            } ?: fallback

            fun attempts(name: String, fallback: Long): Int {
                val value = number(name, fallback)
                require(value in 1..10_000) { "$name must be between 1 and 10000" }
                return value.toInt()
            }

            val proxies = env["LOGIN_TRUSTED_PROXY_CIDRS"]?.let { value ->
                require(value.isNotBlank()) { "LOGIN_TRUSTED_PROXY_CIDRS must be omitted or contain explicit CIDRs" }
                value.split(',').map { ProxyNetwork.parse(it.trim()) }
            } ?: emptyList()

            return LoginRateLimitConfig(
                clientAttempts = attempts("LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS", 5),
                totalAttempts = attempts("LOGIN_RATE_LIMIT_TOTAL_ATTEMPTS", 100),
                windowSeconds = number("LOGIN_RATE_LIMIT_WINDOW_SECONDS", 60),
                trustedProxies = proxies,
            )
        }
    }
}

/**
 * Two rolling request budgets, reserved atomically BEFORE parsing credentials.
 * Admitted requests (including successes and malformed bodies) consume both budgets.
 * Rejections never extend the window. The total budget also bounds stored client keys.
 */
internal class LoginAttemptLimiter(
    private val policy: LoginRateLimitConfig,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private data class Attempt(val client: String, val time: Long)

    private val windowNanos = TimeUnit.SECONDS.toNanos(policy.windowSeconds)
    private val total = ArrayDeque<Attempt>()
    private val clients = mutableMapOf<String, ArrayDeque<Long>>()

    /** Null means admitted; otherwise return positive, rounded-up Retry-After seconds. */
    @Synchronized
    fun retryAfterOrAcquire(client: String): Long? {
        val now = nanoTime()
        while (total.isNotEmpty() && now - total.first.time >= windowNanos) {
            val expired = total.removeFirst()
            val clientAttempts = clients.getValue(expired.client)
            clientAttempts.removeFirst()
            if (clientAttempts.isEmpty()) clients.remove(expired.client)
        }

        val attempts = clients[client]
        val clientWait = if (attempts != null && attempts.size >= policy.clientAttempts) {
            windowNanos - (now - attempts.first)
        } else 0L
        val totalWait = if (total.size >= policy.totalAttempts) {
            windowNanos - (now - total.first.time)
        } else 0L
        val wait = maxOf(clientWait, totalWait)
        if (wait > 0) return (wait + 999_999_999L) / 1_000_000_000L

        total.addLast(Attempt(client, now))
        clients.getOrPut(client) { ArrayDeque() }.addLast(now)
        return null
    }
}
