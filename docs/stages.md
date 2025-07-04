# Essential Loader Stages

essential-loader is split into multiple stages where earlier stages are generally harder to upgrade
and must therefore be less complex.

## stage0

The "zero-th" stage is what mods bundle into their jar directly.
This means that it can never be reliably updated because there could always be a mod that has an older version of it
and if that mod gets loaded first, the older version of stage0 gets to run (at least on the launchwrapper platform).

All which stage0 does is look through all stage1 jars visible on the classpath and at predetermined file locations,
pick the latest one, extract it to a specific file location if necessary and then transfer execution to it by loading
it via a plain `URLClassLoader`.

This allows stage1 to be updated, either because one of the mods bundles a newer version, or out-of-band from a later
stage by downloading an update to the predetermined location.

#### Relocation

Each stage0 includes a stage1 jar which it will load (unless it found a more recent one).\
Note that this stage1 jar file **MUST NOT** be relocated when stage0 is relocated because then it will no longer be
discoverable by all stage0.
I.e. any instance of stage0 code must be able to see all the stage1 jars (so it is able to pick the most recent one),
so they must all be at the same fixed location in each jar.

### LaunchWrapper

On LaunchWrapper, stage0 is implemented as a simple Tweaker class that forwards all tweaker methods to stage1.
Third-party mods may use it either by specifying it as the `TweakClass` in the modjar's manifest directly, or if they
already have a tweaker of their own, by extending it with their tweaker.

It is also safe to be relocated if the mod wishes to do that, though there aren't any direct advantages to doing so.
And take note of the relocation warning

### Fabric

On Fabric, stage0 is included as a regular Jar-in-Jar mod.

As such, it is technically redundant because fabric-loader itself will already pick the latest version when there are
multiple.

It is still used for consistency across platforms and the out-of-band-update functionality (though that too could be
done by just dumping a jar in the mods folder and having fabric-loader discover the newer version that way).

### ModLauncher

On ModLauncher, stage0 is implemented as a `TransformationService`.
It is currently exploded directly into the jar just like on LaunchWrapper.
Though if Forge's Jar-in-Jar functionality is ever deemed stable enough, that may become an option too.

With ModLauncher 9 requiring that no two jars share packages, relocating stage0 is for now a hard requirement.
Failing to do so will cause your mod to be incompatible with other mods that fail to do so.

With `TransformationService` being loaded via Java's `ServiceLoader`, they should not interfere with existing
`TransformationService`s already present in your mod (unlike LaunchWrapper, where each jar can only have one Tweaker).
As such, extending or otherwise interacting directly with Essential's TransformationService or other loader classes
is forbidden.

## stage1

The first stage is relatively simple.
It generally just checks for updates for stage2, downloads the latest version if not already present
(no fancy diff updates; just a plain, simple, low-risk-low-reward download) and then jumps to it like stage0 did.

It is currently exclusively distributed as part of stage0 but may in the future be distributed in other ways too if
there is a need for it.

Unless noted otherwise in the platform section, stage1 also ensures that stage2 is only loaded once per boot, not once
pre stage0 instance of which there may be multiple if they were relocated.

It also does a few platform-specific things necessary to allow the host mod to still function even if the stage2
download fails (assuming the host mod has no hard dependency on Essential).

### LaunchWrapper

On LaunchWrapper, it is even simpler: The stage2 jar file is embedded in the stage1 jar file, so stage1 simply extracts
that (no update checks, downloads, or anything), and then jumps to it like stage0 did.

Stage1 used to do a lot more on LaunchWrapper, but all this functionality has since been moved to stage2.

### Fabric

On Fabric, there's no additional platform-specific code.

Stage1 also does not need to ensure that only one instance of stage2 is loaded because fabric-loader already only ever
loads a single instance of stage0.

### ModLauncher

On ModLauncher, it additionally:
- instructs Forge to scan the host mod jar for regular mods (ordinarily Forge would not check for mods in a jar that
  already contained a TransformationService)
- determines a unique id for the transformation service if the stage2 transformation service cannot be loaded

Stage1 on ModLauncher ensures that only a single instance of stage2 is loaded by placing itself under the
`gg.essential.loader.stage1.loaded` key into the ModLauncher blackboard and only loading stage2 if there's no such entry
yet.


## stage2

The second stage is where all the heavy lifting is done.

It checks for updates with various ways to configure which branch to download from, downloads updates in the most
efficient manner possible (e.g. diffs when a previous version is already available locally) and with a fancy progress
popup, gets the downloaded mod loaded via the native mod loader as far as possible while making the fact that this does
happen as transparent as possible to the mod itself.

It is also the place that houses the vast majority of ugly hacks required to archive the above. Most of these can be
fairly fragile which is why they are in stage2, which by default can receive auto-updates, and as such can be fixed
fairly quickly and without any user effort.

It is distributed currently exclusively via Essential's `mods` API under the slug `essential:loader-stage2` but may in
the future also be pre-bundled into stage0 to allow for offline use / use in an environment where auto-updates are
strongly discouraged (such as large modpacks).

See the `platforms.md` file for details on what this stage does on each platform.

## stage3

This is the mod (currently always Essential) that gets loaded by stage2 and does not live in this repository.
It is also usually not called stage3 unless directly compared with the other stages as it is here.

Currently stage2 makes some assumptions about this mod, long term it should however ideally be able to load any regular
mod that could be loaded on the host mod loader in the same way one would do this on the host mod loader without
auto-updating.

### LaunchWrapper

Once loaded, stage2 will call `gg.essential.api.tweaker.EssentialTweaker.initialize` which will in turn call
`gg.essential.main.Bootstrap.initialize` which will add mixin exclusions, our mixin configs, our mixin error and some
LaunchWrapper transformers to fix bugs in third-party mods.

### Fabric

Once loaded, stage2 will make the same call as on LaunchWrapper. However only the mixin configs and the error handler
are relevant on Fabric.

### ModLauncher

With ModLauncher being the latest addition to essential-loader, stage2 does not explicitly call it at all and stage3
instead relies solely on Mixin discovering its `MixinConfigs` manifest attribute.
