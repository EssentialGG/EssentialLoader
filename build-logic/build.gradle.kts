plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    api("gradle.plugin.com.github.johnrengelman:shadow:7.1.2")

    implementation("org.ow2.asm:asm-commons:9.3")
}
