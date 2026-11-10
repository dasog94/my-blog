package dev.jimmy.blog

import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val INTEGRATED_ORIGIN = "https://blog.example.test"
private enum class Initialization { ENVIRONMENT, VALIDATED_CONFIG }

// Omitted APP_ENV must still mean production. Normalize the HTTPS default port
// before configuring CORS, and use non-default budgets to detect lost wiring.
private val integratedEnvironment = (testEnvironment - "APP_ENV") + mapOf(
    "CORS_ALLOWED_ORIGINS" to "HTTPS://BLOG.EXAMPLE.TEST:443/,https://preview.example.test:8443",
    "LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS" to "2",
    "LOGIN_RATE_LIMIT_TOTAL_ATTEMPTS" to "3",
    "LOGIN_RATE_LIMIT_WINDOW_SECONDS" to "7",
)

private fun ApplicationTestBuilder.withIntegratedApi(
    initialization: Initialization,
    clock: LoginTestClock = LoginTestClock(),
    env: Map<String, String> = integratedEnvironment,
) {
    environment {
        config = MapApplicationConfig("jwt.issuer" to "test-api", "jwt.audience" to "test-web", "jwt.realm" to "Test")
    }
    application {
        when (initialization) {
            Initialization.ENVIRONMENT -> configureBlogApplication(env, { clock.nanos })
            Initialization.VALIDATED_CONFIG -> configureApplication(DeploymentConfig.fromEnvironment(env), { clock.nanos })
        }
    }
}

private suspend fun HttpClient.integratedLogin(
    origin: String = INTEGRATED_ORIGIN,
    password: String = "test-password-only",
): HttpResponse = post("/api/auth/login") {
    header(HttpHeaders.Origin, origin)
    contentType(ContentType.Application.Json)
    setBody(Json.encodeToString(LoginRequest("account@example.test", password)))
}

class DeploymentAuthenticationIntegrationTest {
    @Test
    fun `both initialization paths combine production CORS configured throttling and injected recovery`() {
        for (initialization in Initialization.entries) testApplication {
            val clock = LoginTestClock()
            withIntegratedApi(initialization, clock)
            repeat(5) {
                val preflight = client.options("/api/auth/login") {
                    header(HttpHeaders.Origin, INTEGRATED_ORIGIN)
                    header(HttpHeaders.AccessControlRequestMethod, "POST")
                    header(HttpHeaders.AccessControlRequestHeaders, "content-type")
                }
                assertEquals(HttpStatusCode.OK, preflight.status)
            }
            val login = client.integratedLogin()
            assertEquals(HttpStatusCode.OK, login.status, initialization.name)
            assertEquals(INTEGRATED_ORIGIN, login.headers[HttpHeaders.AccessControlAllowOrigin])
            val session = Json.decodeFromString<LoginResponse>(login.bodyAsText())
            assertEquals(HttpStatusCode.Unauthorized, client.integratedLogin(password = "incorrect").status)

            val blocked = client.integratedLogin()
            assertEquals(HttpStatusCode.TooManyRequests, blocked.status)
            assertEquals("7", blocked.headers[HttpHeaders.RetryAfter])
            assertEquals("no-store", blocked.headers[HttpHeaders.CacheControl])
            assertEquals(INTEGRATED_ORIGIN, blocked.headers[HttpHeaders.AccessControlAllowOrigin])
            assertTrue(blocked.headers[HttpHeaders.AccessControlExposeHeaders].orEmpty().lowercase().contains("retry-after"))
            assertEquals(ErrorResponse("Too many login attempts. Try again later."),
                Json.decodeFromString<ErrorResponse>(blocked.bodyAsText()))

            val me = client.get("/api/auth/me") {
                header(HttpHeaders.Origin, INTEGRATED_ORIGIN)
                bearerAuth(session.token)
            }
            assertEquals(HttpStatusCode.OK, me.status)
            assertEquals(INTEGRATED_ORIGIN, me.headers[HttpHeaders.AccessControlAllowOrigin])
            assertEquals(session.user, Json.decodeFromString<UserResponse>(me.bodyAsText()))
            assertEquals(HttpStatusCode.OK, client.get("/health").status)

            clock.advanceMillis(6_999)
            val stillBlocked = client.integratedLogin()
            assertEquals(HttpStatusCode.TooManyRequests, stillBlocked.status)
            assertEquals("1", stillBlocked.headers[HttpHeaders.RetryAfter])
            clock.advanceMillis(1)
            val recovered = client.integratedLogin(origin = "https://preview.example.test:8443")
            assertEquals(HttpStatusCode.OK, recovered.status)
            assertEquals("https://preview.example.test:8443", recovered.headers[HttpHeaders.AccessControlAllowOrigin])
            assertNull(recovered.headers[HttpHeaders.RetryAfter])
        }
    }

    @Test
    fun `production origin rejection does not spend login capacity in either initialization path`() {
        for (initialization in Initialization.entries) testApplication {
            withIntegratedApi(initialization, env = integratedEnvironment + mapOf(
                "LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS" to "1",
                "LOGIN_RATE_LIMIT_TOTAL_ATTEMPTS" to "1",
            ))
            for (origin in listOf("http://blog.example.test", "https://unlisted.example.test", "https://blog.example.test:8443")) {
                val rejected = client.integratedLogin(origin)
                assertEquals(HttpStatusCode.Forbidden, rejected.status)
                assertNull(rejected.headers[HttpHeaders.AccessControlAllowOrigin])
            }
            assertEquals(HttpStatusCode.OK, client.integratedLogin().status, initialization.name)
            assertEquals(HttpStatusCode.TooManyRequests, client.integratedLogin().status)
        }
    }

    @Test
    fun `runtime environment path rejects invalid deployment and limiter settings before startup`() {
        val invalidEnvironments = listOf(
            "CORS_ALLOWED_ORIGINS" to (integratedEnvironment - "CORS_ALLOWED_ORIGINS"),
            "CORS_ALLOWED_ORIGINS" to (integratedEnvironment + ("CORS_ALLOWED_ORIGINS" to "http://localhost:3000")),
            "APP_ENV" to (integratedEnvironment + ("APP_ENV" to "prod")),
            "BLOG_ACCOUNT_PASSWORD" to (integratedEnvironment - "BLOG_ACCOUNT_PASSWORD"),
            "LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS" to (integratedEnvironment + ("LOGIN_RATE_LIMIT_CLIENT_ATTEMPTS" to "0")),
            "LOGIN_RATE_LIMIT_TOTAL_ATTEMPTS" to (integratedEnvironment + ("LOGIN_RATE_LIMIT_TOTAL_ATTEMPTS" to "0")),
            "LOGIN_RATE_LIMIT_WINDOW_SECONDS" to (integratedEnvironment + ("LOGIN_RATE_LIMIT_WINDOW_SECONDS" to "")),
            "LOGIN_TRUSTED_PROXY_CIDRS" to (integratedEnvironment + ("LOGIN_TRUSTED_PROXY_CIDRS" to "0.0.0.0/0")),
        )
        for ((setting, env) in invalidEnvironments) testApplication {
            withIntegratedApi(Initialization.ENVIRONMENT, env = env)
            val failure = assertFailsWith<Exception>(setting) { startApplication() }
            assertTrue(generateSequence<Throwable>(failure) { it.cause }.any {
                it.message.orEmpty().contains(setting)
            }, "Startup must reject the invalid $setting rather than fail for an unrelated reason")
        }
    }
}
