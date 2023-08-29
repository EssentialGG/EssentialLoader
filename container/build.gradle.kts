import essential.*
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("base")
    id("essential.build-logic")
}

repositories {
    maven("https://repo.sk1er.club/repository/maven-public")
}

val fabric by configurations.creating
val launchwrapper by configurations.creating
val modlauncher8 by configurations.creating
val modlauncher9 by configurations.creating

dependencies {
    fabric(project(":stage0:fabric"))
    launchwrapper(project(":stage0:launchwrapper"))
    modlauncher8(project(":stage0:modlauncher8"))
    modlauncher9(project(":stage0:modlauncher9"))
}

val fabricJar = tasks.register<Jar>("fabricJar") {
    dependsOn(fabric)
    from({ fabric.singleFile }) {
        rename { "essential-loader.jar" }
    }
    from("src/main/resources") {
        include("fabric.mod.json")
    }
}

val launchwrapperJar = tasks.register<Jar>("launchwrapperJar") {
    dependsOn(launchwrapper)
    from({ zipTree(launchwrapper.singleFile) })

    manifest {
        attributes(mapOf(
            "ModSide" to "CLIENT",
            "FMLCorePluginContainsFMLMod" to "Yes, yes it does",
            "TweakClass" to "gg.essential.loader.stage0.EssentialSetupTweaker",
            "TweakOrder" to "0"
        ))
    }
}

fun ShadowJar.configureModLauncherJar(configuration: Configuration) {
    dependsOn(configuration)
    from({ zipTree(configuration.singleFile) })
    preserveJars()

    // We cannot relocate the whole package cause the stage1.jar must remain in the same place
    // and excludes are broken: https://github.com/johnrengelman/shadow/issues/305
    // But we must relocate because modlauncher does not allow the same package to be used in different jars and yet
    // still does not have a JiJ mechanism either (we want to reserve the original package for that JiJ version).
    relocate("gg.essential.loader.stage0.EssentialLoader", "gg.essential.container.loader.stage0.EssentialLoader")
    relocate("gg.essential.loader.stage0.EssentialTransformationService", "gg.essential.container.loader.stage0.EssentialTransformationService")
    relocate("gg.essential.loader.stage0.EssentialTransformationServiceBase", "gg.essential.container.loader.stage0.EssentialTransformationServiceBase")
    relocate("gg.essential.loader.stage0.util", "gg.essential.container.loader.stage0.util")

    // required for service files to be relocated
    mergeServiceFiles()

    manifest {
        attributes(mapOf(
            "FMLModType" to "LIBRARY"
        ))
    }
}

val modlauncher8Jar = tasks.register<ShadowJar>("modlauncher8Jar") {
    configureModLauncherJar(modlauncher8)
}

val modlauncher9Jar = tasks.register<ShadowJar>("modlauncher9Jar") {
    configureModLauncherJar(modlauncher9)
}

tasks.withType<Jar> {
    destinationDirectory.set(project.buildDir.resolve("libs"))
    archiveBaseName.set("${project.name}-${this.name.removeSuffix("Jar")}")

    from("src/main/resources") {
        include("essential_container_marker.txt")
        include("assets/**")
    }
}

tasks.assemble {
    dependsOn(fabricJar)
    dependsOn(launchwrapperJar)
    dependsOn(modlauncher8Jar)
    dependsOn(modlauncher9Jar)
}
