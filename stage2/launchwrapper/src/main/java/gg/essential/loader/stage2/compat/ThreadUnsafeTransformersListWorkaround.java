package gg.essential.loader.stage2.compat;

import net.minecraft.launchwrapper.IClassTransformer;
import net.minecraft.launchwrapper.Launch;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

// Registering a transformer is not a thread safe operation.
// Usually this isn't an issue because all transformers are registered early during boot where there is only one
// thread.
// Forge however also registers a transformer way later when loading its mods, and at that point other threads may
// already be active, so thread safety becomes a concern and classes may randomly fail to load due to
// `ConcurrentModificationException`s.
// This method patches the issue by replacing the transformers list with a copy-on-write one.
public class ThreadUnsafeTransformersListWorkaround {
    @SuppressWarnings("unchecked")
    public static void apply() {
        try {
            LaunchClassLoader classLoader = Launch.classLoader;
            Field field = LaunchClassLoader.class.getDeclaredField("transformers");
            field.setAccessible(true);
            List<IClassTransformer> value = (List<IClassTransformer>) field.get(classLoader);
            if (value instanceof CopyOnWriteArrayList) {
                LogManager.getLogger().debug("LaunchClassLoader.transformers appears to already be copy-on-write");
                return;
            }
            LogManager.getLogger().debug("Replacing LaunchClassLoader.transformers list with a copy-on-write list");
            field.set(classLoader, new CopyOnWriteArrayList<>(value));
        } catch (Throwable t) {
            LogManager.getLogger().error(
                "Failed to replace plain LaunchClassLoader.transformers list with copy-on-write one", t);
        }
    }
}
