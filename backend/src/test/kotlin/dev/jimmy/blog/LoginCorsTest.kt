package dev.jimmy.blog

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val FRONTEND_ORIGIN = "https://blog.example.test"

private fun ApplicationTestBuilder.withLoginApi() {
    environment {
        config = MapApplicationConfig(
            "jwt.issuer" to "test-api",
            "jwt.audience" to "test-frontend",
            "jwt.realm" to "Test realm",
        )
    }
    application { configureApplication(DeploymentConfig.fromEnvironment(testEnvironment)) }
}

class LoginCorsTest {
    @Test
    fun `JSON login preflight accepts the frontend origin and content type`() = testApplication {
        withLoginApi()
        val response = client.options("/api/auth/login") {
            header(HttpHeaders.Origin, FRONTEND_ORIGIN)
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "content-type")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(FRONTEND_ORIGIN, response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertTrue(response.headers[HttpHeaders.AccessControlAllowHeaders].orEmpty().lowercase().contains("content-type"))
        assertFalse(response.headers[HttpHeaders.AccessControlAllowOrigin] == "*")
    }

    @Test
    fun `login token authorizes the current user and both responses are browser readable`() = testApplication {
        withLoginApi()
        val login = client.post("/api/auth/login") {
            header(HttpHeaders.Origin, FRONTEND_ORIGIN)
            contentType(ContentType.Application.Json)
            setBody("""{"email":"ACCOUNT@example.test","password":"test-password-only"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
        assertEquals(FRONTEND_ORIGIN, login.headers[HttpHeaders.AccessControlAllowOrigin])
        val session = Json.decodeFromString<LoginResponse>(login.bodyAsText())
        assertTrue(session.token.isNotBlank())
        assertEquals(UserResponse("test-user", "Test User", "account@example.test"), session.user)

        val preflight = client.options("/api/auth/me") {
            header(HttpHeaders.Origin, FRONTEND_ORIGIN)
            header(HttpHeaders.AccessControlRequestMethod, "GET")
            header(HttpHeaders.AccessControlRequestHeaders, "authorization")
        }
        assertEquals(HttpStatusCode.OK, preflight.status)
        assertEquals(FRONTEND_ORIGIN, preflight.headers[HttpHeaders.AccessControlAllowOrigin])
        assertTrue(preflight.headers[HttpHeaders.AccessControlAllowHeaders].orEmpty().lowercase().contains("authorization"))

        val me = client.get("/api/auth/me") {
            header(HttpHeaders.Origin, FRONTEND_ORIGIN)
            bearerAuth(session.token)
        }
        assertEquals(HttpStatusCode.OK, me.status)
        assertEquals(FRONTEND_ORIGIN, me.headers[HttpHeaders.AccessControlAllowOrigin])
        assertEquals(session.user, Json.decodeFromString<UserResponse>(me.bodyAsText()))
    }

    @Test
    fun `wrong credentials return a browser readable 401 without issuing a token`() = testApplication {
        withLoginApi()
        val response = client.post("/api/auth/login") {
            header(HttpHeaders.Origin, FRONTEND_ORIGIN)
            contentType(ContentType.Application.Json)
            setBody("""{"email":"account@example.test","password":"incorrect-password"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
        assertEquals(FRONTEND_ORIGIN, response.headers[HttpHeaders.AccessControlAllowOrigin])
        assertEquals(ErrorResponse("Email or password is incorrect."), Json.decodeFromString<ErrorResponse>(response.bodyAsText()))
    }

    @Test
    fun `missing and invalid bearer tokens return browser readable unauthorized responses`() = testApplication {
        withLoginApi()
        for (token in listOf(null, "not-a-jwt")) {
            val response = client.get("/api/auth/me") {
                header(HttpHeaders.Origin, FRONTEND_ORIGIN)
                if (token != null) bearerAuth(token)
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(FRONTEND_ORIGIN, response.headers[HttpHeaders.AccessControlAllowOrigin])
        }
    }

    @Test
    fun `unlisted origins are rejected on preflight and actual login`() = testApplication {
        withLoginApi()
        for (origin in listOf("https://unlisted.example.test", "http://blog.example.test", "https://blog.example.test:8443", "null")) {
            val preflight = client.options("/api/auth/login") {
                header(HttpHeaders.Origin, origin)
                header(HttpHeaders.AccessControlRequestMethod, "POST")
                header(HttpHeaders.AccessControlRequestHeaders, "content-type")
            }
            assertEquals(HttpStatusCode.Forbidden, preflight.status, origin)
            assertNull(preflight.headers[HttpHeaders.AccessControlAllowOrigin], origin)

            val login = client.post("/api/auth/login") {
                header(HttpHeaders.Origin, origin)
                contentType(ContentType.Application.Json)
                setBody("""{"email":"account@example.test","password":"test-password-only"}""")
            }
            assertEquals(HttpStatusCode.Forbidden, login.status, origin)
            assertNull(login.headers[HttpHeaders.AccessControlAllowOrigin], origin)
        }
    }

    @Test
    fun `unapproved request headers are rejected during preflight`() = testApplication {
        withLoginApi()
        val response = client.options("/api/auth/login") {
            header(HttpHeaders.Origin, FRONTEND_ORIGIN)
            header(HttpHeaders.AccessControlRequestMethod, "POST")
            header(HttpHeaders.AccessControlRequestHeaders, "content-type,x-not-allowed")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertNull(response.headers[HttpHeaders.AccessControlAllowOrigin])
    }

    @Test
    fun `health endpoint still works without browser origin or authentication`() = testApplication {
        withLoginApi()
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }
}
