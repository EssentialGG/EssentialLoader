# Container Mods

A plain stage0 jar of essential-loader by default will dynamically load the latest version of Essential
but it can also be configured to load any other mod or a specific branch (e.g. `beta` or `staging` instead of just
`stable`) of a mod.

Such an essential-using dummy mod that's just a stage0 with an embedded configuration file and no functionality of its
own is called a container mod because it is just a thin container for the loader rather than the actual mod.

But container mods need not just be thin, they can also have embedded a specific version of the mod, in which case they
will always load that version and be fully usable in an offline environment while also having the option to
programmatically enable auto-updates or click-to-update updates if the user wishes to do so.
Such a container mod is then called a "pinned container mod".

If auto-updates are enabled, the container mod will behave just like a regular thin container jar, ignoring the embedded
version.
\
With click-to-update, the user will be notified in-game when there is an update, and they can decide there on a
case-by-case basis whether to upgrade to that version or stay on the current one.

## Configuration

### The container mod

The container mod is configured via a `essential-loader.properties` file at the root of the jar:
```properties
# The publisher and mod slug as set in the Essential Mods Panel
publisherSlug=essential
modSlug=examplemod
# Optionally, the publisher and mod ID as displayed in the Essential Mods Panel
publisherId=26d49e5b60ecf53d6b26c76a
modId=60ecf53d6b26c76a26d49e5b
# A display name for use in UI and log messages
displayName=ExampleMod
# Optional, the branch from which updates should be pulled, defaults to `stable`
branch=stable
```

#### Pinning

In addition to above configuration, one must embed (not explode) the pinned jar into the container jar and additional
specify the following values:
```properties
# The path to the embedded mod jar inside the container jar. Should be unique to this mod+version so it cannot clash
# with other mods or versions of the same mod.
# Must include the leading slash, otherwise it will be read as a regular URL, not a path inside the jar.
pinnedFile=/path-to-embedded.jar
# MD5 checksum of the above file
pinnedFileMd5=d41d8cd98f00b204e9800998ecf8427e
# Optional, the version as named in the Essential Mods Panel of the embedded jar.
# Required if click-to-update is used.
# May be required by your mod if it wants to know its own version.
# Also required for diff-updates, otherwise the full jar will be downloaded on the first update.
pinnedFileVersion=1.2.0
# Optional, the ID of the version of the embedded jar.
# If provided, may be used instead of the human-readable version in certain places. Currently unused.
pinnedFileVersionId=6b26c76a26d49e5b60ecf53d
```

### Enabling updates

In addition to the embedded configuration, a container mod may be further configured programmatically (e.g. via an
ingame user interface) to enable/disable auto-updates or to switch to a specific version/branch.

This configuration file is located at `.minecraft/essential/mods/$pub_$mod/essential-loader.properties`
where `$pub` is the mod's `publisherSlug` and `$mod` is the mod's `modSlug`. If these two value are identical, the
entire `$pub_$mod` folder name is shortened to juts `$mod`.
\
For legacy reasons the configuration file for Essential itself is at `.minecraft/essential/essential-loader.properties`.

It may contain the following values:
```properties
# Whether update checks are enabled.
# When set to `true`:
#   The mod is updated to the latest version at `branch` on each game boot. Pinned versions are ignored.
# When set to `false`:
#   If a version is already downloaded, loads that version; otherwise downloads the latest version once or uses the
#   latest pinned version if there is one.
# When set to `with-prompt`:
#   Checks for updates and sets the `pendingUpdateVersion` property if one is found.
#   The mod is then expected to show an in-game update prompt and write to the `pendingUpdateResolution` property
#   either `true` if the user accepted the upgrade or `false` if they want to ignore it.
#   If the mod fails to write to the property, the loader will show its own prompt to allow the user to update even if
#   the current mod version is bricked.
# Defaults to `true` unless there is a pinned jar present in which case it defaults to `with-prompt`.
autoUpdate=true
# Specifies the branch to follow if autoUpdate is enabled.
branch=beta
# Set by the loader when there is an update available.
# This value **MUST NOT BE MODIFIED** by the mod.
# Instead `pendingUpdateResolution` should be set.
pendingUpdateVersion=1.2.0.13
# Should be set by the mod when it finds the `pendingUpdateVersion` property to be set.
# If the mod fails to write the property, the loader will show its own prompt to allow the user to update even if the
# current mod version is bricked.
# Should be set to `true` to accept and download the update.
# Should be set to `false` to ignore this specific update.
pendingUpdateResolution=true
# INTERNAL USE ONLY
# Stores the last version to which the mod has accepted upgrading.
# Prevents downgrading when the container mod is upgraded (e.g. by a modpack author) to a version that's older than
# this version:
# If a pinned jar is found that's older than this version, then that jar is ignored.
# If however a pinned jar is found that's newer than or equal to this version, then that jar will be used and this
# property will be un-set to allow the user to downgrade the mod by downgrading the container mod.
overridePinnedVersion=1.2.0.12
```

## Pinning stage2

Stage2 can be pinned as well.
(But only on Fabric and ModLauncher. On LaunchWrapper the stage2 jar is already included in the stage1 jar and cannot
be updated independently.)

Instead of an `essential-loader.properties` file at the root of the container mod, the file must be placed at
`gg/essential/loader/stage1/stage2.properties`.
Only the `pinnedFile`, `pinnedFileMd5` and `pinnedFileVersion` properties are supported and all three are required.

For external configuration file for stage2 is at `.minecraft/essential/loader/stage1/$variant/config.properties`.
It provides has the `autoUpdate`, `branch`, `pendingUpdateVersion` and `pendingUpdateResolution` properties with
identical semantic meaning to the per-mod config files.

TODO this needs some more work before we can open it up for third-party usage!
Given stage2 is shared between all mods, we need to have a way to enable
auto-updates if one of the mods does not have it pinned, i.e. we only want it pinned if all the mods agree that it
should be pinned. Currently a single mod can pin it for all. Kinda tricky with old loaders around though.
