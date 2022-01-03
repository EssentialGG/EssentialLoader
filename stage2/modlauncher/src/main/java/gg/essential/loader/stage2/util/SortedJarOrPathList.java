package gg.essential.loader.stage2.util;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * List which keeps its JarOrPath elements sorted by their module version, latest first.
 *
 * See [EssentialTransformationService.configureLayerToBeSortedByVersion].
 */
public class SortedJarOrPathList extends ArrayList<Object> {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final ArtifactVersion FALLBACK_VERSION = new DefaultArtifactVersion("1");
    private Function<Object, SecureJar> jarGetter;
    private Function<SecureJar, JarMetadata> metadataGetter;

    private final Map<Object, ArtifactVersion> versionCache = new IdentityHashMap<>();

    private final Comparator<Object> COMPARATOR =
        Comparator.comparing(pathOrJar -> versionCache.computeIfAbsent(pathOrJar, this::getVersion)).reversed();

    private ArtifactVersion getVersion(Object pathOrJar) {
        SecureJar jar = getJar(pathOrJar);
        if (jar == null) return FALLBACK_VERSION;
        JarMetadata metadata = getMetadata(jar);
        if (metadata == null) return FALLBACK_VERSION;
        return new DefaultArtifactVersion(metadata.version());
    }

    private SecureJar getJar(Object pathOrJar) {
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
        boolean changed = super.add(o);
        sort(COMPARATOR);
        return changed;
    }

    @Override
    public boolean addAll(Collection<?> c) {
        boolean changed = super.addAll(c);
        sort(COMPARATOR);
        return changed;
    }
}
