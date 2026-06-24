package gg.essential.loader.stage2.util;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.NamedPath;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Accessor for ModLauncher's private `PathOrJar` class used by the `layers` map.
 * That class wraps either a `NamedPath` or a `SecureJar`.
 * <br>
 * Prior to the introduction of `PathOrJar`, `SecureJar`s were used directly.
 * On these older versions, we simply passes through the `SecureJars`,
 * allowing our code to just always act as if `PathOrJar` exists, even if it doesn't yet.
 */
public class PathOrJarAccessor {
    private static final Logger LOGGER = LogManager.getLogger();
    private static final BiFunction<NamedPath, SecureJar, Object> constructor;
    private static final Function<Object, SecureJar> jarGetter;

    static {
        BiFunction<NamedPath, SecureJar, Object> constructorFunc = (path, jar) -> null;
        Function<Object, SecureJar> jarGetterFunc = (pathOrJar) -> null;
        try {
            IModuleLayerManager layerManager = Launcher.INSTANCE.findLayerManager().orElseThrow();

            Field layersField = layerManager.getClass().getDeclaredField("layers");
            layersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<IModuleLayerManager.Layer, List<Object>> layers =
                (Map<IModuleLayerManager.Layer, List<Object>>) layersField.get(layerManager);

            Class<?> pathOrJarClass =
                layers.values().stream()
                    .flatMap(Collection::stream)
                    .findAny()
                    .orElseThrow() // should always at least contain our own jar
                    .getClass();

            if (SecureJar.class.isAssignableFrom(pathOrJarClass)) {
                constructorFunc = (path, jar) -> jar;
                jarGetterFunc = (pathOrJar) -> (SecureJar) pathOrJar;
            } else {
                Constructor<?> constructor = pathOrJarClass.getDeclaredConstructors()[0];
                constructor.setAccessible(true);
                constructorFunc = (path, jar) -> {
                    try {
                        return constructor.newInstance(path, jar);
                    } catch (Throwable t) {
                        LOGGER.error("Failed to construct PathOrJar:", t);
                        return null;
                    }
                };

                Field jarField = pathOrJarClass.getDeclaredField("jar");
                jarField.setAccessible(true);
                jarGetterFunc = wrapper -> {
                    try {
                        return (SecureJar) jarField.get(wrapper);
                    } catch (Throwable t) {
                        LOGGER.error("Failed to get jar from PathOrJar:", t);
                        return null;
                    }
                };
            }
        } catch (Throwable t) {
            LOGGER.error("Failed to initialize:", t);
        }
        constructor = constructorFunc;
        jarGetter = jarGetterFunc;
    }

    public static @Nullable Object from(SecureJar jar) {
        return constructor.apply(null, jar);
    }

    public static @Nullable SecureJar getJar(Object pathOrJar) {
        return jarGetter.apply(pathOrJar);
    }
}
