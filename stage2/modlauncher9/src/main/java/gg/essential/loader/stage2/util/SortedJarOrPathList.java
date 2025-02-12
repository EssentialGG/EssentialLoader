package gg.essential.loader.stage2.util;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.NamedPath;
import gg.essential.loader.stage2.modlauncher.CompatibilityLayer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.jar.Manifest;

/**
 * List which keeps its JarOrPath elements sorted by their module version, latest first.
 *
 * See [EssentialTransformationService.configureLayerToBeSortedByVersion].
 */
public class SortedJarOrPathList extends ArrayList<Object> {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ArtifactVersion FALLBACK_VERSION = new DefaultArtifactVersion("1");
    private static Function<Object, SecureJar> jarGetter;
    private Function<SecureJar, JarMetadata> metadataGetter;
    private BiFunction<NamedPath, SecureJar, Object> pathOrJarConstructor;

    private final Map<Object, ArtifactVersion> versionCache = new IdentityHashMap<>();

    private final Comparator<Object> COMPARATOR =
        Comparator.comparing(pathOrJar -> versionCache.computeIfAbsent(pathOrJar, this::getVersion)).reversed();

    private final CompatibilityLayer compatibilityLayer;

    // This does not actually have anything to do with the original functionality of this class but we needed an entry
    // point for replacing one jar (KFF with old Kotlin) with another jar (same KFF but with newer Kotlin merged into
    // it) and this class is perfect for that.
    private final Function<SecureJar, List<SecureJar>> substitute;

    public SortedJarOrPathList(CompatibilityLayer compatibilityLayer, Function<SecureJar, List<SecureJar>> substitute) {
        this.compatibilityLayer = compatibilityLayer;
        this.substitute = substitute;
    }

    private ArtifactVersion getVersion(Object pathOrJar) {
        SecureJar jar = getJar(pathOrJar);
        if (jar == null) return FALLBACK_VERSION;
        JarMetadata metadata = getMetadata(jar);
        if (metadata == null) return FALLBACK_VERSION;
        String version = metadata.version();
        if (version == null) return FALLBACK_VERSION;
        // ModLauncher somehow manages to report the version of some jars (really unsure which ones, best guess
        // right now is all those without a `FMLModType` manifest attribute? seemingly completely irregardless of
        // whether they have an `Implementation-Version` attribute!)
        // as "Optional.empty" (yes, that's a String, somewhere someone must have blindly `toString`ed).
        // We'll just go fetch it ourselves then I guess.
        if (version.equals("Optional.empty")) {
            version = null;
        }
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

    public static SecureJar getJar(Object pathOrJar) {
        if (pathOrJar instanceof SecureJar secureJar) {
            return secureJar;
        }

        if (jarGetter == null) {
            try {
                Field jarField = pathOrJar.getClass().getDeclaredField("jar");
                jarField.setAccessible(true);
                jarGetter = wrapper -> {
                    try {
                        return (SecureJar) jarField.get(wrapper);
                    } catch (Throwable t) {
                        LOGGER.error("Failed to get jar from PathOrJar:", t);
                        return null;
                    }
                };
            } catch (Throwable t) {
                LOGGER.error("Failed to get jar from PathOrJar:", t);
                jarGetter = __ -> null;
            }
        }
        return jarGetter.apply(pathOrJar);
    }

    private JarMetadata getMetadata(SecureJar jar) {
        if (metadataGetter == null) {
            try {
                metadataGetter = UnsafeHacks.makeGetter(jar.getClass().getDeclaredField("metadata"));
            } catch (Throwable t) {
                LOGGER.error("Failed to get metadata from " + jar.getClass() + ":", t);
                metadataGetter = __ -> null;
            }
        }
        return metadataGetter.apply(jar);
    }

    @Override
    public boolean add(Object o) {
        //noinspection RedundantCollectionOperation
        return addAll(List.of(o));
    }

    @Override
    public boolean addAll(Collection<?> c) {
        boolean changed = super.addAll(c.stream().flatMap(it -> substitute(it).stream()).toList());
        sort(COMPARATOR);
        return changed;
    }

    private List<?> substitute(Object orgPathOrJar) {
        SecureJar orgJar = getJar(orgPathOrJar);
        if (orgJar == null) {
            return List.of(orgPathOrJar);
        }

        List<SecureJar> newJars = substitute.apply(orgJar);
        if (newJars == null) {
            return List.of(orgPathOrJar);
        }

        if (orgPathOrJar instanceof SecureJar) {
            return newJars;
        }

        if (pathOrJarConstructor == null) {
            try {
                Constructor<?> constructor = orgPathOrJar.getClass().getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                pathOrJarConstructor = (path, jar) -> {
                    try {
                        return constructor.newInstance(path, jar);
                    } catch (Throwable t) {
                        LOGGER.error("Failed to construct PathOrJar:", t);
                        return null;
                    }
                };
            } catch (Throwable t) {
                LOGGER.error("Failed to construct PathOrJar:", t);
                pathOrJarConstructor = (path, jar) -> null;
            }
        }
        List<Object> newPathOrJars = new ArrayList<>(newJars.size());
        for (SecureJar newJar : newJars) {
            Object newPathOrJar = pathOrJarConstructor.apply(null, newJar);
            if (newPathOrJar == null) {
                return List.of(orgPathOrJar);
            }
            newPathOrJars.add(newPathOrJar);
        }

        return newPathOrJars;
    }
}
