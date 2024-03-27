package net.minecraftforge.fml.loading;

import gg.essential.loader.stage2.util.UnsafeHacks;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A LanguageLoadingProvider which sorts all jars by their Implementation-Version (latest first) and only picks the most
 * recent one for each Implementation-Name.
 */
public class SortedLanguageLoadingProvider extends LanguageLoadingProvider {
    private static final ArtifactVersion FALLBACK_VERSION = new DefaultArtifactVersion("1");
    private static final Function<ModFile, Manifest> manifestGetter = UnsafeHacks.makeGetter(ModFile.class, "manifest");
    private static final Map<ModFile, ArtifactVersion> versionCache = new WeakHashMap<>();

    // IMPORTANT: This class must not have any constructors or non-static field initializers!
    //            It is instantiated via `Unsafe.allocateInstance` (because it cannot call the package-private super
    //            constructor), so its constructor is never invoked.

    public List<Path> extraHighPriorityFiles;

    @Override
    public void addAdditionalLanguages(List<ModFile> modFiles) {
        if (modFiles == null) {
            return;
        }

        Set<String> visited = new HashSet<>();

        Stream<ModFile> filteredFiles = modFiles.stream()
            .sorted(Comparator.comparing(this::getVersion).reversed())
            .filter(modFile -> visited.add(getName(modFile)));

        Stream<ModFile> extraFiles = extraHighPriorityFiles.stream()
            .map(path -> new ModFile(path, null, null));

        modFiles = Stream.concat(extraFiles, filteredFiles)
            .collect(Collectors.toList());

        super.addAdditionalLanguages(modFiles);
    }

    private static Attributes getLangProviderAttributes(ModFile modFile) {
        Manifest manifest = manifestGetter.apply(modFile);
        for (Attributes attributes : manifest.getEntries().values()) {
            String vendor = attributes.getValue(Attributes.Name.SPECIFICATION_VENDOR);
            String title = attributes.getValue(Attributes.Name.SPECIFICATION_TITLE);
            if ("Forge".equals(vendor) && "Mod Language Provider".equals(title)) {
                return attributes;
            }
        }
        return new Attributes();
    }

    private static String getName(ModFile modFile) {
        Attributes attributes = getLangProviderAttributes(modFile);
        String title = attributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE);
        return title != null ? title : modFile.getFileName();
    }

    private ArtifactVersion getVersion(ModFile modFile) {
        return versionCache.computeIfAbsent(modFile, __ -> {
            Attributes attributes = getLangProviderAttributes(modFile);
            String versionStr = attributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION);
            return versionStr != null ? new DefaultArtifactVersion(versionStr) : FALLBACK_VERSION;
        });
    }

    // Widened access because we need to call this just like forge does
    @Override
    public void addForgeLanguage(Path forgePath) {
        super.addForgeLanguage(forgePath);
    }
}
