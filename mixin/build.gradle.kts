import essential.CompatMixinTask
import gg.essential.gradle.util.prebundle
import gg.essential.gradle.util.RelocationTransform.Companion.registerRelocationAttribute

plugins {
    id("java-library")
    id("gg.essential.defaults") version "0.6.7" apply false // for the relocation utils
    id("gg.essential.defaults.maven-publish") version "0.6.7"
    id("essential.build-logic")
}

val patchesVersion = "0.1.0"
val mixinVersion = "0.8.4"
val asmVersion = "5.2"

version = "$patchesVersion+mixin.$mixinVersion"
java.toolchain.languageVersion.set(JavaLanguageVersion.of(8))

repositories {
    mavenCentral()
    maven("https://repo.spongepowered.org/repository/maven-releases/")
    maven("https://libraries.minecraft.net")
}

val relocated = registerRelocationAttribute("essential-guava21-relocated") {
    relocate("com.google.common", "gg.essential.lib.guava21")
    relocate("com.google.thirdparty.publicsuffix", "gg.essential.lib.guava21.publicsuffix")
}

val fatMixinContent by configurations.creating {
    attributes { attribute(relocated, true) }
}
val fatMixin by configurations.creating
configurations.api { extendsFrom(fatMixin) }
val asm by configurations.creating
configurations.compileOnly { extendsFrom(asm) }

dependencies {
    fatMixinContent("org.spongepowered:mixin:$mixinVersion")
    // this is usually provided by MC but 1.8.9's is too old, so we need to bundle (and relocate) our own
    fatMixinContent("com.google.guava:guava:21.0")

    // Our special mixin which has its Guava 21 dependency relocated, so it can run alongside Guava 17
    fatMixin(prebundle(fatMixinContent))
    // Mixin needs at least asm 5.2 but older versions provide only 5.0.3
    asm("org.ow2.asm:asm-debug-all:$asmVersion")

    compileOnly("net.minecraft:launchwrapper:1.12")
}

tasks.processResources {
    val expansions = mapOf(
        "mixinVersion" to mixinVersion,
        "patchesVersion" to patchesVersion,
        "asmVersion" to asmVersion,
    )
    inputs.property("expansions", expansions)
    filesMatching("essential.mod.json") {
        expand(expansions)
    }
}

val patchedJar by tasks.registering(CompatMixinTask::class) {
    mixinClasses.from(sourceSets.main.map { it.output })
    input.set(fatMixin.files.single())
    output.set(buildDir.resolve("patched.jar"))
}

tasks.jar {
    from(patchedJar.flatMap { it.output }.map { zipTree(it) }) {
        // Signatures were invalidated by patching
        exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
        // Legacy Forge chokes on these (and they are useless for it anyway cause it only supports Java 8)
        exclude("**/module-info.class")
        exclude("META-INF/versions/9/**")
        // Same with these coming from Mixin for ModLauncher9 support
        exclude("org/spongepowered/asm/launch/MixinLaunchPlugin.class")
        exclude("org/spongepowered/asm/launch/MixinTransformationService.class")
        exclude("org/spongepowered/asm/launch/platform/container/ContainerHandleModLauncherEx*")
    }

    dependsOn(asm)
    from({ asm.files.single() }) {
        into("META-INF/jars")
    }
}

publishing {
    publications {
        named<MavenPublication>("maven") {
            artifactId = "mixin"
        }
    }
}
