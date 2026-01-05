## Internals
For an explanation of how the internals of Essential Loader on various platforms function, see the [docs](https://github.com/EssentialGG/EssentialLoader/tree/master/docs) folder.

## Using Essential Loader with your mod on 1.8.9 - 1.12.2
Essential Loader for Minecraft 1.8.9 through 1.12.2 (commonly called "legacy Forge" or "LaunchWrapper") provides support
for Mixin 0.8.x (even on 1.8.9, and with improved third-party mod compatibility on 1.12.2) and Jar-in-Jar style
dependency management (e.g. if two mods ship different versions of the same dependency, it will automatically select
the more recent one).

### Setup
To use it with your mod, firstly you must declare a dependency in your build script and include it in your jar file:
```kotlin
repositories {
    maven("https://repo.essential.gg/repository/maven-public/")
}

val embed by configurations.creating
configurations.implementation.get().extendsFrom(embed)

dependencies {
    embed("gg.essential:loader-launchwrapper:1.3.0")
}

tasks.jar {
    // Embed the contents of the Essential Loader jar into your mod jar.
    dependsOn(embed)
    from(embed.files.map { zipTree(it) })
    // Set Essential Loader as the Tweaker for your mod.
    // If you already have a custom Tweaker, you can have it extend the class instead.
    // If you previously used the Mixin Tweaker, you can simply use Essential Loader instead, it'll automatically
    // initialize Mixin for any mod with a `MixinConfigs` attribute.
    manifest.attributes(mapOf("TweakClass" to "gg.essential.loader.stage0.EssentialSetupTweaker"))
}
```
You then need to create a `essential.mod.json` file in your `src/main/resources` folder which defines some metadata and
specifies which libraries your mod includes and where inside the mod jar they can be found:
```json
{
    "id": "your_mod_id_goes_here",
    "version": "1.0.0",
    "jars": [
        {
            "id": "com.example:examplelib",
            "version": "0.1.0",
            "file": "META-INF/jars/examplelib-0.1.0.jar"
        }
    ]
}
```
If you don't have any special needs, you can auto-generate the `version` and `jars` entries and automate the inclusion
of the libraries in your mod based on Gradle dependencies with the following snippet in your build script:
```kotlin
val jij by configurations.creating
configurations.implementation.get().extendsFrom(jij)

dependencies {
    // Add all the dependencies you wish to jar-in-jar to the custom `jij` configuration
    jij("com.example:examplelib:0.1.0")
}

tasks.processResources {
    val expansions = mapOf(
        "version" to version,
        "jars" to provider {
            jij.resolvedConfiguration.resolvedArtifacts.joinToString(",\n") { artifact ->
                val id = artifact.moduleVersion.id
                """
                    {
                        "id": "${id.group}:${id.name}",
                        "version": "${id.version}",
                        "file": "META-INF/jars/${artifact.file.name}"
                    }
                """.trimIndent()
            }
        },
    )
    inputs.property("expansions", expansions)
    filesMatching("essential.mod.json") {
        expand(expansions)
    }
}

tasks.jar {
    dependsOn(jij)
    from(jij.files) {
        into("META-INF/jars")
    }
}
```
and the following `essential.mod.json` template file:
```json
{
  "id": "your_mod_id_goes_here",
  "version": "${version}",
  "jars": [
    ${jars.get()}
  ]
}
```

### Mixin 0.8.x

To use our Mixin with your mod, assuming you've already included Essential Loader as per above instructions, and you've
already set up Mixin's annotation processor and refmap generation via your build system (this is done the same way as
it would be done with stock Mixin 0.8.x or 0.7.10, Essential Loader only affects things at runtime), simply
add it as a jar-in-jar dependency:
```kotlin
dependencies {
    jij("gg.essential:mixin:0.1.0+mixin.0.8.4")

    // MixinExtras may be added in the same way:
    // Essential Loader will automatically initialize it for you, no need to call `MixinExtrasBootstrap`.
    // `annotationProcessor` is necessary to generate refmaps if your build system does not setup MixinExtras for you.
    jij(annotationProcessor("io.github.llamalad7:mixinextras-common:0.4.1")!!)
    // or if you've previously used Essential's relocated MixinExtras version:
    jij(annotationProcessor("gg.essential.lib:mixinextras:0.4.0")!!)
}
```
