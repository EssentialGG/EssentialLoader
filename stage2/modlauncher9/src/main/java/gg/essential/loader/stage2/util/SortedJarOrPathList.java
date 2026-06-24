package gg.essential.loader.stage2.util;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import gg.essential.loader.stage2.modlauncher.CompatibilityLayer;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.jar.Manifest;

public class SortedJarOrPathList implements Consumer<List<Object>> {
    private static final ArtifactVersion FALLBACK_VERSION = new DefaultArtifactVersion("1");

    private final Map<Object, ArtifactVersion> versionCache = new IdentityHashMap<>();

    private final Comparator<Object> COMPARATOR =
        Comparator.comparing(pathOrJar -> versionCache.computeIfAbsent(pathOrJar, this::getVersion)).reversed();

    private final CompatibilityLayer compatibilityLayer;

    public SortedJarOrPathList(CompatibilityLayer compatibilityLayer) {
        this.compatibilityLayer = compatibilityLayer;
    }

    @Override
    public void accept(List<Object> pathOrJarList) {
        pathOrJarList.sort(COMPARATOR);
    }

    private ArtifactVersion getVersion(Object pathOrJar) {
        SecureJar jar = PathOrJarAccessor.getJar(pathOrJar);
        if (jar == null) return FALLBACK_VERSION;

        String version = null;

        JarMetadata metadata = JarMetadataAccessor.getMetadata(jar);
        if (metadata != null) {
            version = metadata.version();
        }

        // Some revisions of ModLauncher have a bug where they simply call `toString` on `Optional<String>`, resulting
        // in versions being reported as the string "Optional.empty" or "Optional[1.2.3]" instead of `null` or "1.2.3".
        // See https://github.com/McModLauncher/securejarhandler/blob/7cd8481364d73bacecf2b608479c6b903bff7f6c/src/main/java/cpw/mods/jarhandling/impl/ModuleJarMetadata.java#L137
        // We need to unwrap those to get at the real version.
        if (version != null && version.equals("Optional.empty")) {
            version = null;
        } else if (version != null && version.startsWith("Optional[")) {
            version = version.substring("Optional[".length(), version.length() - 1);
        }

        // Additionally, when ModuleJarMetadata is used (not entirely sure when that's the case, at the very least the
        // jar must have a `module-info.class` but mods may also use ModJarMetadata instead), ModLauncher only looks at
        // the version declared in the `module-info.class`. For most jars that version is `null` though because it
        // requires extra setup in Gradle which most people don't do.
        // We need a version for correct sorting though, so we'll try to find one ourselves.
        if (version == null) {
            Manifest manifest = compatibilityLayer.getManifest(jar);
            if (manifest != null) {
                version = manifest.getMainAttributes().getValue("Implementation-Version");
            }
        }
        // and if that doesn't work (some of the Kotlin libs, e.g. kotlinx-serialization-json-jvm-1.7.3, don't have
        // such an attribute), then we'll take a guess based on the file name
        if (version == null) {
            String name = jar.getPrimaryPath().getFileName().toString();
            if (name.contains("-") && name.endsWith(".jar")) {
                version = name.substring(name.lastIndexOf("-") + 1, name.length() - ".jar".length());
            }
        }

        if (version == null) {
            return FALLBACK_VERSION;
        }
        return new DefaultArtifactVersion(version);
    }
}
