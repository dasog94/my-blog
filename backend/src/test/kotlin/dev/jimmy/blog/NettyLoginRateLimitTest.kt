package dev.jimmy.blog

import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals

/** Uses a real socket: Ktor's in-process test host reports the literal "localhost" as its peer. */
class NettyLoginRateLimitTest {
    private fun withSocketApi(
        overrides: Map<String, String>,
        test: (LoginTestClock, (List<String>?, String) -> HttpResponse<String>) -> Unit,
    ) = runBlocking {
        val clock = LoginTestClock()
        val server = embeddedServer(Netty, environment = applicationEnvironment {
            config = MapApplicationConfig("jwt.issuer" to "test-api", "jwt.audience" to "test-web", "jwt.realm" to "Test")
        }, configure = {
            connector { host = "127.0.0.1"; port = 0 }
        }) {
            configureBlogApplication(loginRateLimitTestEnvironment + overrides, { clock.nanos })
        }.start(wait = false)
        try {
            val port = server.engine.resolvedConnectors().single().port
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().use { client ->
                val login = { forwardedFor: List<String>?, password: String ->
                    val builder = HttpRequest.newBuilder(URI("http://127.0.0.1:$port/api/auth/login"))
                        .timeout(Duration.ofSeconds(5))
                        .header("Content-Type", "application/json")
                    forwardedFor?.forEach { builder.header("X-Forwarded-For", it) }
                    val request = builder.POST(HttpRequest.BodyPublishers.ofString(
                        Json.encodeToString(LoginRequest("account@example.test", password)),
                    )).build()
                    client.send(request, HttpResponse.BodyHandlers.ofString())
                }
                test(clock, login)
            }
        } finally {
            server.stop(0, 1000)
        }
    }

    @Test
    fun `trusted transport separates clients ignores forged prefixes and recovers aggregate capacity`() = withSocketApi(mapOf(
        "LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS" to "2",
        "LOGIN_RATE_LIMIT_TOTAL_ATTEMPTS" to "3",
        "LOGIN_TRUSTED_PROXY_CIDRS" to "127.0.0.1/32",
    )) { clock, login ->
        repeat(2) { index ->
            assertEquals(401, login(listOf("203.0.113.$index", "198.51.100.1"), "wrong").statusCode())
        }
        assertEquals(429, login(listOf("203.0.113.99, 198.51.100.1"), "test-password-only").statusCode())
        // A second client still has capacity, until the process-wide third admission.
        assertEquals(200, login(listOf("198.51.100.2"), "test-password-only").statusCode())
        val blocked = login(listOf("198.51.100.3"), "wrong")
        assertEquals(429, blocked.statusCode())
        assertEquals("60", blocked.headers().firstValue("Retry-After").orElse(null))
        clock.advanceMillis(60_000)
        assertEquals(200, login(listOf("198.51.100.3"), "test-password-only").statusCode())
    }

    @Test
    fun `invalid trusted proxy metadata cannot supply a fallback budget or issue a token`() = withSocketApi(mapOf(
        "LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS" to "1",
        "LOGIN_TRUSTED_PROXY_CIDRS" to "127.0.0.1/32",
    )) { _, login ->
        assertEquals(401, login(listOf("198.51.100.1"), "wrong").statusCode())
        for (headers in listOf(null, listOf("unknown"), listOf("127.0.0.1"),
            listOf("x".repeat(4097) + ", 198.51.100.1"))) {
            assertEquals(400, login(headers, "test-password-only").statusCode())
        }
        assertEquals(429, login(listOf("198.51.100.1"), "test-password-only").statusCode())
        assertEquals(200, login(listOf("198.51.100.2"), "test-password-only").statusCode())
    }

    @Test
    fun `actual transport peer remains the key when forwarding trust is unset`() = withSocketApi(mapOf(
        "LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS" to "1",
    )) { _, login ->
        assertEquals(200, login(listOf("198.51.100.1"), "test-password-only").statusCode())
        assertEquals(429, login(listOf("198.51.100.2"), "wrong").statusCode())
    }
}
