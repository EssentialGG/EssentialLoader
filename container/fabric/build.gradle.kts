plugins {
    id("base")
    id("essential.build-logic")
}

val stage0 by configurations.creating { isCanBeConsumed = false }

dependencies {
    stage0(project(":stage0:fabric"))
}

val jar by tasks.registering(Jar::class) {
    destinationDirectory.set(project.buildDir.resolve("libs"))
    archiveBaseName.set("container-${project.name}")

    dependsOn(stage0)
    from({ stage0.singleFile }) {
        rename { "essential-loader.jar" }
    }

    from("resources")
}

artifacts {
    add("default", jar)
}
