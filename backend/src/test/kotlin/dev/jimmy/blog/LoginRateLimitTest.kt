package dev.jimmy.blog

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val ORIGIN = "https://blog.example.test"
internal val loginRateLimitTestEnvironment = mapOf(
    "JWT_SECRET" to "login-rate-limit-test-secret",
    "BLOG_ACCOUNT_ID" to "test-user",
    "BLOG_ACCOUNT_NAME" to "Test User",
    "BLOG_ACCOUNT_EMAIL" to "account@example.test",
    "BLOG_ACCOUNT_PASSWORD" to "test-password-only",
    "CORS_ALLOWED_ORIGINS" to ORIGIN,
)

private fun ApplicationTestBuilder.withLoginApi(
    clock: LoginTestClock = LoginTestClock(),
    overrides: Map<String, String> = emptyMap(),
) {
    environment {
        config = MapApplicationConfig("jwt.issuer" to "test-api", "jwt.audience" to "test-web", "jwt.realm" to "Test")
    }
    application { configureBlogApplication(loginRateLimitTestEnvironment + overrides, { clock.nanos }) }
}

private suspend fun HttpClient.login(
    password: String = "wrong-password",
    email: String = "account@example.test",
    extraHeaders: Map<String, String> = emptyMap(),
): HttpResponse = post("/api/auth/login") {
    header(HttpHeaders.Origin, ORIGIN)
    extraHeaders.forEach { (name, value) -> header(name, value) }
    contentType(ContentType.Application.Json)
    setBody(Json.encodeToString(LoginRequest(email, password)))
}

class LoginRateLimitTest {
    @Test
    fun `repeated failures return 401 then 429 with browser readable retry information and no token`() = testApplication {
        withLoginApi()
        repeat(5) { assertEquals(HttpStatusCode.Unauthorized, client.login().status) }
        val response = client.login()
        assertEquals(HttpStatusCode.TooManyRequests, response.status)
        assertEquals("60", response.headers[HttpHeaders.RetryAfter])
        assertEquals("no-store", response.headers[HttpHeaders.CacheControl])
        assertEquals(ORIGIN, response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertTrue(response.headers[HttpHeaders.AccessControlExposeHeaders].orEmpty().lowercase().contains("retry-after"))
        assertEquals(ErrorResponse("Too many login attempts. Try again later."),
            Json.decodeFromString<ErrorResponse>(response.bodyAsText()))
    }

    @Test
    fun `correct credentials are blocked during cooldown and work at the configured expiry`() = testApplication {
        val clock = LoginTestClock()
        withLoginApi(clock, mapOf("LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS" to "2", "LOGIN_RATE_LIMIT_WINDOW_SECONDS" to "10"))
        repeat(2) { assertEquals(HttpStatusCode.Unauthorized, client.login().status) }
        assertEquals(HttpStatusCode.TooManyRequests, client.login("test-password-only").status)
        clock.advanceMillis(9_999)
        val blocked = client.login("test-password-only")
        assertEquals(HttpStatusCode.TooManyRequests, blocked.status)
        assertEquals("1", blocked.headers[HttpHeaders.RetryAfter])
        clock.advanceMillis(1)
        val recovered = client.login("test-password-only", "ACCOUNT@example.test")
        assertEquals(HttpStatusCode.OK, recovered.status)
        assertNull(recovered.headers[HttpHeaders.RetryAfter])
        val session = Json.decodeFromString<LoginResponse>(recovered.bodyAsText())
        assertTrue(session.token.isNotBlank())
        assertEquals("test-user", session.user.id)
        assertEquals(HttpStatusCode.OK, client.get("/api/auth/me") { bearerAuth(session.token) }.status)
    }

    @Test
    fun `legitimate login and existing authenticated routes work while login alone is throttled`() = testApplication {
        withLoginApi()
        val login = client.login("test-password-only")
        assertEquals(HttpStatusCode.OK, login.status)
        val session = Json.decodeFromString<LoginResponse>(login.bodyAsText())
        repeat(4) { assertEquals(HttpStatusCode.Unauthorized, client.login().status) }
        assertEquals(HttpStatusCode.TooManyRequests, client.login().status)
        assertEquals(HttpStatusCode.OK, client.get("/health").status)
        val me = client.get("/api/auth/me") { bearerAuth(session.token) }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals(session.user, Json.decodeFromString<UserResponse>(me.bodyAsText()))
        assertEquals(HttpStatusCode.NoContent, client.post("/api/auth/logout") { bearerAuth(session.token) }.status)
        val preflight = client.options("/api/auth/login") {
            header(HttpHeaders.Origin, ORIGIN)
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "content-type")
        }
        assertEquals(HttpStatusCode.OK, preflight.status)
    }

    @Test
    fun `varying usernames and untrusted IP headers cannot bypass a client limit`() = testApplication {
        withLoginApi()
        repeat(5) { index ->
            assertEquals(HttpStatusCode.Unauthorized, client.login(email = "user$index@example.test", extraHeaders = mapOf(
                "X-Forwarded-For" to "198.51.100.$index",
                "X-Real-IP" to "203.0.113.$index",
                "CF-Connecting-IP" to "198.51.100.$index",
                "True-Client-IP" to "203.0.113.$index",
                "Forwarded" to "for=198.51.100.$index",
            )).status)
        }
        assertEquals(HttpStatusCode.TooManyRequests, client.login(extraHeaders = mapOf("X-Forwarded-For" to "203.0.113.99")).status)
    }

    @Test
    fun `malformed JSON consumes the same budget before credentials are parsed`() = testApplication {
        withLoginApi(overrides = mapOf("LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS" to "1"))
        val malformed = client.post("/api/auth/login") { contentType(ContentType.Application.Json); setBody("{") }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
        // A blocked malformed body is never parsed, and valid credentials cannot skip the exhausted budget.
        assertEquals(HttpStatusCode.TooManyRequests, client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("{")
        }.status)
        assertEquals(HttpStatusCode.TooManyRequests, client.login("test-password-only").status)
    }

    @Test
    fun `successful logins consume capacity and do not reset the budget`() = testApplication {
        withLoginApi(overrides = mapOf("LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS" to "2"))
        repeat(2) { assertEquals(HttpStatusCode.OK, client.login("test-password-only").status) }
        assertEquals(HttpStatusCode.TooManyRequests, client.login("test-password-only").status)
    }

    @Test
    fun `parallel failures cannot race past the configured endpoint limit`() = testApplication {
        withLoginApi()
        val responses = coroutineScope { List(30) { async { client.login() } }.awaitAll() }
        assertEquals(5, responses.count { it.status == HttpStatusCode.Unauthorized })
        assertEquals(25, responses.count { it.status == HttpStatusCode.TooManyRequests })
    }

    @Test
    fun `credential failures are audited without credentials or raw header data and rejection cannot amplify logs`() = testApplication {
        withLoginApi(overrides = mapOf("LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS" to "2"))
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        val logger = LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME) as Logger
        logger.addAppender(appender)
        try {
            repeat(2) {
                assertEquals(HttpStatusCode.Unauthorized, client.login("private-password", "private-email@example.test",
                    mapOf("X-Forwarded-For" to "untrusted-header-sentinel")).status)
            }
            repeat(10) { assertEquals(HttpStatusCode.TooManyRequests, client.login().status) }
            val events = appender.list.filter { it.formattedMessage.startsWith("auth.login.failed client=") }
            assertEquals(2, events.size)
            for (event in events) {
                assertTrue(listOf("private-password", "private-email", "untrusted-header-sentinel")
                    .none { it in event.formattedMessage })
            }
        } finally {
            logger.detachAppender(appender)
            appender.stop()
        }
    }
}
