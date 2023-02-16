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
# If this property is set, regardless of its value, then the pinned version is ignored completely.
# When set to true: the mod is updated to the latest version at `branch` on each game boot
# When set to false: if a version is already downloaded, loads that version, otherwise downloads the latest version once
autoUpdate=true
# Specifies the branch to follow if autoUpdate is enabled.
# Note that specific version names are valid targets. So you can implement click-to-update by fetching the latest
# version ingame and then setting this property to its name whenever the user requests to update.
branch=beta
```

## Pinning stage2

Stage2 can be pinned as well, however it is less configurable.

To pin a stage2 jar, place it at `gg/essential/loader/stage1/stage2.jar` inside the container mod.

For external configuration file for stage2 is at `.minecraft/essential/loader/stage1/$variant/config.properties`.
It provides the `autoUpdate` and the `branch` options with identical semantic meaning to the per-mod config files.

TODO this needs some more work before we can open it up for third-party usage!
Given stage2 is shared between all mods, we need to pick the latest version and also have a way to enable
auto-updates if one of the mods does not have it pinned, i.e. we only want it pinned if all the mods agree that it
should be pinned. Currently a single mod can pin it for all. Kinda tricky with old loaders around though.
