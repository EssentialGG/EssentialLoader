package gg.essential.loader.stage2;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import gg.essential.loader.stage2.modlauncher.CompatibilityLayer;
import gg.essential.loader.stage2.util.DelegatingJarMetadata;
import gg.essential.loader.stage2.util.Lazy;
import gg.essential.loader.stage2.util.SortedJarOrPathList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;

import static gg.essential.loader.stage2.util.KFFMerger.isJarJarKff;

/**
 * A jar metadata which takes the name of another jar with the same packages in the same layer.
 *
 * By default, ModLauncher derives module names from the jar file. As a result, they are generally unstable, and we
 * cannot upgrade an old version by shipping a newer version with the same name (cause the existing older one might have
 * a different module name, in which case they'll conflict instead).
 * This jar metadata works around that by automatically "renaming" our jar if it can find another jar with overlapping
 * packages (this may result in false positives, but such cases would have crashed the game due to non-unique exports
 * anyway).
 */
public class SelfRenamingJarMetadata extends DelegatingJarMetadata {
    private static final Logger LOGGER = LogManager.getLogger(SelfRenamingJarMetadata.class);
    private static final ThreadLocal<Boolean> RE_ENTRANCE_LOCK = ThreadLocal.withInitial(() -> false);

    private final CompatibilityLayer compatibilityLayer;
    private final Lazy<SecureJar> secureJar;

    public SelfRenamingJarMetadata(CompatibilityLayer compatibilityLayer, Lazy<SecureJar> secureJar, JarMetadata delegate) {
        super(delegate);
        this.compatibilityLayer = compatibilityLayer;
        this.secureJar = secureJar;
    }

    @Override
    public String name() {
        if (RE_ENTRANCE_LOCK.get()) {
            throw new SelfRenamingReEntranceException();
        } else {
            RE_ENTRANCE_LOCK.set(true);
        }
        String defaultName = delegate.name();
        Set<String> ourPackages = compatibilityLayer.getPackages(secureJar.get());
        try {
            for (SecureJar otherJar : getLayerJars()) {
                String otherModuleName;
                try {
                    otherModuleName = otherJar.name();
                } catch (SelfRenamingReEntranceException ignored) {
                    continue;
                }
                Set<String> otherPackages = compatibilityLayer.getPackages(otherJar);
                if (otherPackages.stream().anyMatch(ourPackages::contains)) {
                    LOGGER.debug("Found existing module with name {}, renaming {} to match.", otherModuleName, defaultName);
                    return otherModuleName;
                }
                // Special case for fully-JarJar-reliant KFF which no longer contains any code itself and doesn't
                // declare its module name in its manifest either.
                // We still need to make the KFF we ship to use the same module name though, because otherwise it'll
                // be loaded and we'll end up with two modules exporting Kotlin.
                if (defaultName.equals("thedarkcolour.kotlinforforge") && otherPackages.isEmpty() && isJarJarKff(otherJar)) {
                    LOGGER.debug("Found existing JarJar KFF with name {}, renaming {} to match.", otherModuleName, defaultName);
                    return otherModuleName;
                }
            }
        } catch (Throwable e) {
            LOGGER.error("Exception occurred while trying to self-rename module " + defaultName + ": ", e);
        } finally {
            RE_ENTRANCE_LOCK.set(false);
        }
        LOGGER.debug("Did not find any existing modules to rename {}.", defaultName);
        return defaultName;
    }

    @SuppressWarnings("unchecked")
    private List<Object> getLayerElements() throws Throwable {
        IModuleLayerManager layerManager = Launcher.INSTANCE.findLayerManager().orElseThrow();
        Field layersField = layerManager.getClass().getDeclaredField("layers");
        layersField.setAccessible(true);
        EnumMap<IModuleLayerManager.Layer, List<Object>> map =
            (EnumMap<IModuleLayerManager.Layer, List<Object>>) layersField.get(layerManager);
        IModuleLayerManager.Layer[] layers = IModuleLayerManager.Layer.values();
        for (int i = layers.length - 1; i >= 0; i--) {
            List<Object> elements = map.get(layers[i]);
            if (elements != null) {
                return elements;
            }
        }
        throw new RuntimeException("Failed to find current layer?!");
    }

    private List<SecureJar> getLayerJars() throws Throwable {
        List<SecureJar> jars = new ArrayList<>();
        for (Object pathOrJar : getLayerElements()) {
            SecureJar jar = SortedJarOrPathList.getJar(pathOrJar);
            if (jar != null) {
                jars.add(jar);
            }
        }
        return jars;
    }

    private static class SelfRenamingReEntranceException extends RuntimeException {
    }
}
