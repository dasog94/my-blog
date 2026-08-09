plugins {
    kotlin("jvm") version "2.2.20"
    id("io.ktor.plugin") version "3.3.3"
    kotlin("plugin.serialization") version "2.2.20"
}

group = "dev.jimmy.blog"
version = "0.1.0"

repositories { mavenCentral() }

dependencies {
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-auth")
    implementation("io.ktor:ktor-server-auth-jwt")
    implementation("ch.qos.logback:logback-classic:1.5.21")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation(kotlin("test"))
}

application { mainClass.set("io.ktor.server.netty.EngineMain") }

kotlin { jvmToolchain(21) }
