package dev.jimmy.blog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

internal val testEnvironment = mapOf(
    "APP_ENV" to "production",
    "JWT_SECRET" to "integration-test-only-jwt-secret",
    "BLOG_ACCOUNT_ID" to "test-user",
    "BLOG_ACCOUNT_NAME" to "Test User",
    "BLOG_ACCOUNT_EMAIL" to "account@example.test",
    "BLOG_ACCOUNT_PASSWORD" to "test-password-only",
    "CORS_ALLOWED_ORIGINS" to "https://blog.example.test",
)

class DeploymentConfigTest {
    @Test
    fun `production requires an explicit origin including when APP_ENV is omitted`() {
        for (env in listOf(testEnvironment, testEnvironment - "APP_ENV")) {
            val error = assertFailsWith<IllegalStateException> {
                DeploymentConfig.fromEnvironment(env - "CORS_ALLOWED_ORIGINS")
            }
            assertTrue(error.message.orEmpty().contains("CORS_ALLOWED_ORIGINS"))
        }
    }

    @Test
    fun `development alone gets the localhost fallback`() {
        val config = DeploymentConfig.fromEnvironment(
            (testEnvironment - "CORS_ALLOWED_ORIGINS") + ("APP_ENV" to "development"),
        )
        assertEquals(listOf(CorsOrigin("http", "localhost:3000")), config.corsOrigins)
    }

    @Test
    fun `unknown or empty APP_ENV fails instead of silently enabling development`() {
        for (value in listOf("", " ", "prod", "staging", "test", "developmnt")) {
            val error = assertFailsWith<IllegalStateException> {
                DeploymentConfig.fromEnvironment(testEnvironment + ("APP_ENV" to value))
            }
            assertTrue(error.message.orEmpty().contains("APP_ENV"))
        }
    }

    @Test
    fun `normalizes origins and retains explicit nondefault ports`() {
        val config = DeploymentConfig.fromEnvironment(
            testEnvironment + (
                "CORS_ALLOWED_ORIGINS" to
                    " HTTPS://BLOG.EXAMPLE.TEST:443/, https://preview.example.test:8443, https://blog.example.test"
                ),
        )
        assertEquals(
            listOf(CorsOrigin("https", "blog.example.test"), CorsOrigin("https", "preview.example.test:8443")),
            config.corsOrigins,
        )
    }

    @Test
    fun `rejects malformed or overly broad origins in every environment`() {
        val invalid = listOf(
            "", " ", "*", "null", "blog.example.test", "//blog.example.test",
            "ftp://blog.example.test", "https://*.example.test", "https://blog.example.test/path",
            "https://blog.example.test?x=1", "https://blog.example.test#fragment",
            "https://blog.example.test?", "https://blog.example.test#",
            "https://user:password@blog.example.test", "https://blog.example.test:",
            "https://blog.example.test:0", "https://blog.example.test:65536", "https://blog.example.test:-1",
            "https://blog.example.test:not-a-port", "https://blog.example.test,", ",https://blog.example.test",
            "https://blog.example.test,,https://preview.example.test", "https://blog.example.test\\other",
            "https://[fe80::1%25eth0]",
        )
        for (appEnv in listOf("production", "development")) {
            for (origin in invalid) {
                val error = assertFailsWith<IllegalArgumentException>("$appEnv accepted $origin") {
                    DeploymentConfig.fromEnvironment(
                        testEnvironment + mapOf("APP_ENV" to appEnv, "CORS_ALLOWED_ORIGINS" to origin),
                    )
                }
                assertTrue(error.message.orEmpty().contains("CORS_ALLOWED_ORIGINS"))
            }
        }
    }

    @Test
    fun `production rejects HTTP and local hosts including numeric loopback aliases`() {
        for (origin in listOf(
            "http://blog.example.test", "http://localhost:3000", "https://localhost",
            "https://LOCALHOST.", "https://preview.localhost", "https://127.0.0.1:3000",
            "https://127.12.34.56", "https://0.0.0.0", "https://2130706433",
            "https://0x7f000001", "https://[::1]", "https://[0:0:0:0:0:0:0:1]",
            "https://[::ffff:127.0.0.1]", "https://[::]",
        )) {
            assertFailsWith<IllegalArgumentException>("production accepted $origin") {
                DeploymentConfig.fromEnvironment(testEnvironment + ("CORS_ALLOWED_ORIGINS" to origin))
            }
        }
    }

    @Test
    fun `development accepts explicit local HTTP origins and IPv6`() {
        val config = DeploymentConfig.fromEnvironment(
            testEnvironment + mapOf(
                "APP_ENV" to "development",
                "CORS_ALLOWED_ORIGINS" to "http://localhost:3000,http://127.0.0.1:3001,http://[::1]:3002",
            ),
        )
        assertEquals(
            listOf(CorsOrigin("http", "localhost:3000"), CorsOrigin("http", "127.0.0.1:3001"), CorsOrigin("http", "[::1]:3002")),
            config.corsOrigins,
        )
    }

    @Test
    fun `missing and blank account settings fail at configuration time`() {
        for (name in listOf("JWT_SECRET", "BLOG_ACCOUNT_PASSWORD", "BLOG_ACCOUNT_ID", "BLOG_ACCOUNT_NAME", "BLOG_ACCOUNT_EMAIL")) {
            for (env in listOf(testEnvironment - name, testEnvironment + (name to " "))) {
                val error = assertFailsWith<IllegalStateException> { DeploymentConfig.fromEnvironment(env) }
                assertTrue(error.message.orEmpty().contains(name))
            }
        }
    }

    @Test
    fun `validation errors never echo the supplied origin`() {
        val error = assertFailsWith<IllegalArgumentException> {
            DeploymentConfig.fromEnvironment(
                testEnvironment + ("CORS_ALLOWED_ORIGINS" to "https://accidental-secret@blog.example.test"),
            )
        }
        assertTrue(!error.message.orEmpty().contains("accidental-secret"))
    }
}
