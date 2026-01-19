plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    api("com.gradleup.shadow:com.gradleup.shadow.gradle.plugin:8.3.9")

    implementation("org.ow2.asm:asm-commons:9.3")
}
