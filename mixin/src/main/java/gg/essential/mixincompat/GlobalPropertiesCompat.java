package gg.essential.mixincompat;

import gg.essential.CompatMixin;
import gg.essential.CompatShadow;
import org.spongepowered.asm.launch.GlobalProperties;

@CompatMixin(GlobalProperties.class)
public abstract class GlobalPropertiesCompat {
    @CompatShadow
    public static <T> T get(GlobalProperties.Keys key) { throw new LinkageError(); }

    @CompatShadow
    public static <T> T get(GlobalProperties.Keys key, T defaultValue) { throw new LinkageError(); }

    @CompatShadow
    public static String getString(GlobalProperties.Keys key, String defaultValue) { throw new LinkageError(); }

    @CompatShadow
    public static void put(GlobalProperties.Keys key, Object value) { throw new LinkageError(); }

    public static Object get(String key) {
        return get(GlobalProperties.Keys.of(key));
    }

    public static Object get(String key, Object defaultValue) {
        return get(GlobalProperties.Keys.of(key), defaultValue);
    }

    public static String getString(String key, String defaultValue) {
        return getString(GlobalProperties.Keys.of(key), defaultValue);
    }

    public static void put(String key, Object value) {
        put(GlobalProperties.Keys.of(key), value);
    }
}
