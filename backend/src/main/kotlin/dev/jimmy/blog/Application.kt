package dev.jimmy.blog

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.util.Date
import java.net.URI

@Serializable data class LoginRequest(val email: String, val password: String)
@Serializable data class UserResponse(val id: String, val name: String, val email: String)
@Serializable data class LoginResponse(val token: String, val user: UserResponse)
@Serializable data class ErrorResponse(val message: String)

fun main(args: Array<String>) = EngineMain.main(args)

fun Application.module() {
    val secret = requiredEnv("JWT_SECRET")
    val accountPassword = requiredEnv("BLOG_ACCOUNT_PASSWORD")
    val accountUser = UserResponse(
        id = requiredEnv("BLOG_ACCOUNT_ID"),
        name = requiredEnv("BLOG_ACCOUNT_NAME"),
        email = requiredEnv("BLOG_ACCOUNT_EMAIL"),
    )
    val issuer = environment.config.property("jwt.issuer").getString()
    val audience = environment.config.property("jwt.audience").getString()
    val realm = environment.config.property("jwt.realm").getString()

    install(ContentNegotiation) { json() }
    install(CORS) {
        val origins = (System.getenv("CORS_ALLOWED_ORIGINS") ?: "http://localhost:3000")
            .split(",").map { it.trim() }
        for (origin in origins) {
            val uri = URI(origin)
            require(uri.scheme in listOf("http", "https") && uri.host != null &&
                uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                uri.rawPath.orEmpty() in listOf("", "/")) {
                "CORS_ALLOWED_ORIGINS must contain comma-separated HTTP(S) origins"
            }
            allowHost(uri.rawAuthority, schemes = listOf(uri.scheme))
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Post)
    }
    install(Authentication) {
        jwt("auth-jwt") {
            this.realm = realm
            verifier(JWT.require(Algorithm.HMAC256(secret)).withAudience(audience).withIssuer(issuer).build())
            validate { credential ->
                credential.payload.getClaim("userId").asString()?.let { JWTPrincipal(credential.payload) }
            }
        }
    }

    routing {
        get("/health") { call.respondText("ok") }

        post("/api/auth/login") {
            val request = call.receive<LoginRequest>()
            if (request.email.lowercase() != accountUser.email.lowercase() || request.password != accountPassword) {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse("Email or password is incorrect."))
                return@post
            }
            val token = JWT.create()
                .withAudience(audience).withIssuer(issuer)
                .withClaim("userId", accountUser.id)
                .withExpiresAt(Date(System.currentTimeMillis() + 60 * 60 * 1000))
                .sign(Algorithm.HMAC256(secret))
            call.respond(LoginResponse(token, accountUser))
        }

        authenticate("auth-jwt") {
            get("/api/auth/me") {
                val userId = call.principal<JWTPrincipal>()?.payload?.getClaim("userId")?.asString()
                if (userId != accountUser.id) call.respond(HttpStatusCode.NotFound)
                else call.respond(accountUser)
            }
            post("/api/auth/logout") { call.respond(HttpStatusCode.NoContent) }
        }
    }
}

private fun requiredEnv(name: String): String =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: error("Required environment variable $name is not set")
