package gg.essential.loader.stage2;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.module.ModuleDescriptor;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Set;

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
public class SelfRenamingJarMetadata implements JarMetadata {
    private static final Logger LOGGER = LogManager.getLogger(SelfRenamingJarMetadata.class);
    private static final ThreadLocal<Boolean> RE_ENTRANCE_LOCK = ThreadLocal.withInitial(() -> false);

    private final SecureJar secureJar;
    private final JarMetadata delegate;
    private final IModuleLayerManager.Layer layer;

    public SelfRenamingJarMetadata(SecureJar secureJar, Path path, IModuleLayerManager.Layer layer) {
        this.secureJar = secureJar;
        this.delegate = JarMetadata.from(secureJar, path);
        this.layer = layer;
    }

    @Override
    public String name() {
        if (RE_ENTRANCE_LOCK.get()) {
            throw new SelfRenamingReEntranceException();
        } else {
            RE_ENTRANCE_LOCK.set(true);
        }
        String defaultName = delegate.name();
        Set<String> ourPackages = secureJar.getPackages();
        try {
            for (SecureJar otherJar : getLayerJars()) {
                String otherModuleName;
                try {
                    otherModuleName = otherJar.name();
                } catch (SelfRenamingReEntranceException ignored) {
                    continue;
                }
                if (otherJar.getPackages().stream().anyMatch(ourPackages::contains)) {
                    LOGGER.debug("Found existing module with name {}, renaming {} to match.", otherModuleName, defaultName);
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
        return ((EnumMap<IModuleLayerManager.Layer, List<Object>>) layersField.get(layerManager)).get(this.layer);
    }

    private List<SecureJar> getLayerJars() throws Throwable {
        Field jarField = null;

        List<SecureJar> jars = new ArrayList<>();
        for (Object pathOrJar : getLayerElements()) {
            if (jarField == null) {
                jarField = pathOrJar.getClass().getDeclaredField("jar");
                jarField.setAccessible(true);
            }
            SecureJar jar = (SecureJar) jarField.get(pathOrJar);
            if (jar != null) {
                jars.add(jar);
            }
        }
        return jars;
    }

    @Override
    public String version() {
        return delegate.version();
    }

    @Override
    public ModuleDescriptor descriptor() {
        return delegate.descriptor();
    }

    private static class SelfRenamingReEntranceException extends RuntimeException {
    }
}
