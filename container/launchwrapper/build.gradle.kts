plugins {
    id("base")
    id("essential.build-logic")
}

val stage0 by configurations.creating { isCanBeConsumed = false }

dependencies {
    stage0(project(":stage0:launchwrapper"))
}

val jar by tasks.registering(Jar::class) {
    destinationDirectory.set(layout.buildDirectory.dir("libs"))
    archiveBaseName.set("container-${project.name}")

    dependsOn(stage0)
    from({ zipTree(stage0.singleFile) })

    from("resources")

    manifest {
        attributes(mapOf(
            "ModSide" to "CLIENT",
            "FMLCorePluginContainsFMLMod" to "Yes, yes it does",
            "TweakClass" to "gg.essential.loader.stage0.EssentialSetupTweaker",
            "TweakOrder" to "0"
        ))
    }
}

artifacts {
    add("default", jar)
}
