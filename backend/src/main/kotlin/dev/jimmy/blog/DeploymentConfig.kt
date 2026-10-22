package dev.jimmy.blog

import java.net.InetAddress
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

internal data class CorsOrigin(val scheme: String, val authority: String)

/** Read once, before installing plugins or serving requests. Never log account secrets. */
internal class DeploymentConfig private constructor(
    val jwtSecret: String,
    val accountPassword: String,
    val accountUser: UserResponse,
    val corsOrigins: List<CorsOrigin>,
) {
    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): DeploymentConfig {
            val production = when (env["APP_ENV"]?.trim() ?: "production") {
                "production" -> true
                "development" -> false
                else -> error("APP_ENV must be production or development")
            }
            val rawOrigins = env["CORS_ALLOWED_ORIGINS"]
                ?: if (production) error("CORS_ALLOWED_ORIGINS is required in production")
                else "http://localhost:3000"
            val origins = rawOrigins.split(",").map { parseOrigin(it.trim(), production) }.distinct()

            fun required(name: String): String =
                env[name]?.takeIf { it.isNotBlank() }
                    ?: error("Required environment variable $name is not set")

            return DeploymentConfig(
                jwtSecret = required("JWT_SECRET"),
                accountPassword = required("BLOG_ACCOUNT_PASSWORD"),
                accountUser = UserResponse(
                    id = required("BLOG_ACCOUNT_ID"),
                    name = required("BLOG_ACCOUNT_NAME"),
                    email = required("BLOG_ACCOUNT_EMAIL"),
                ),
                corsOrigins = origins,
            )
        }

        private fun parseOrigin(value: String, production: Boolean): CorsOrigin {
            // Error messages identify the variable, without echoing an accidentally pasted secret.
            val invalid = "CORS_ALLOWED_ORIGINS must contain non-empty, comma-separated HTTP(S) origins " +
                "without credentials, paths, queries, fragments, wildcards, or invalid ports"
            val uri = try {
                URI(value)
            } catch (_: URISyntaxException) {
                throw IllegalArgumentException(invalid)
            }
            val scheme = uri.scheme?.lowercase(Locale.ROOT)
            val host = uri.host?.lowercase(Locale.ROOT)
            require(
                scheme in listOf("http", "https") && host != null &&
                    uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null &&
                    uri.rawPath.orEmpty() in listOf("", "/") &&
                    '*' !in value && '%' !in host.orEmpty() &&
                    (uri.port == -1 || uri.port in 1..65535) &&
                    !uri.rawAuthority.orEmpty().endsWith(":"),
            ) { invalid }
            if (production) {
                require(scheme == "https" && !isLocalHost(host!!)) {
                    "CORS_ALLOWED_ORIGINS must use HTTPS and non-local hosts in production; " +
                        "set APP_ENV=development only for local development"
                }
            }
            val defaultPort = if (scheme == "https") 443 else 80
            val authority = if (uri.port == -1 || uri.port == defaultPort) host!! else "$host:${uri.port}"
            return CorsOrigin(scheme!!, authority)
        }

        private fun isLocalHost(host: String): Boolean {
            val name = host.removePrefix("[").removeSuffix("]").trimEnd('.')
            if (name == "localhost" || name.endsWith(".localhost")) return true
            // Only parse numeric IPv6 literals here; never perform a DNS lookup for hostnames.
            if (':' in name) {
                val address = InetAddress.getByName(name)
                return address.isLoopbackAddress || address.isAnyLocalAddress
            }
            val address = ipv4Address(name) ?: return false
            return address == 0L || address ushr 24 == 127L
        }

        /** Recognize browser IPv4 spellings such as 127.1, 2130706433, and 0x7f000001. */
        private fun ipv4Address(host: String): Long? {
            val parts = host.split('.')
            if (parts.size !in 1..4) return null
            val numbers = parts.map { part ->
                when {
                    part.startsWith("0x", ignoreCase = true) -> part.drop(2).toLongOrNull(16)
                    part.length > 1 && part.startsWith('0') -> part.drop(1).toLongOrNull(8)
                    else -> part.toLongOrNull(10)
                }?.takeIf { it >= 0 } ?: return null
            }
            if (numbers.dropLast(1).any { it > 255 }) return null
            val lastLimit = 1L shl (8 * (5 - parts.size))
            if (numbers.last() >= lastLimit) return null
            return numbers.dropLast(1).foldIndexed(numbers.last()) { index, address, part ->
                address + (part shl (8 * (3 - index)))
            }
        }
    }
}
