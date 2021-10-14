package gg.essential.loader.stage2.relaunch.mixin;

import org.spongepowered.asm.service.IGlobalPropertyService;
import org.spongepowered.asm.service.IPropertyKey;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dummy property service for our relaunch mixin subsystem backed by a simple map.
 */
public class RelaunchGlobalPropertyService implements IGlobalPropertyService {

    private static final Map<String, Object> map = new ConcurrentHashMap<>();

    public RelaunchGlobalPropertyService() {
        if (RelaunchMixinService.loader == null) {
            throw new UnsupportedOperationException("RelaunchMixinService.boot has not been called");
        }
    }

    @Override
    public IPropertyKey resolveKey(String name) {
        return new Key(name);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getProperty(IPropertyKey key) {
        return (T) map.get(((Key) key).str);
    }

    @Override
    public void setProperty(IPropertyKey key, Object value) {
        map.put(((Key) key).str, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getProperty(IPropertyKey key, T defaultValue) {
        return (T) map.getOrDefault(((Key) key).str, defaultValue);
    }

    @Override
    public String getPropertyString(IPropertyKey key, String defaultValue) {
        return getProperty(key, defaultValue);
    }

    private static class Key implements IPropertyKey {
        private final String str;

        private Key(String str) {
            this.str = str;
        }
    }
}
