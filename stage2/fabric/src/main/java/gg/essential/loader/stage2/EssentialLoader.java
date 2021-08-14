package gg.essential.loader.stage2;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixins;

import java.io.File;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EssentialLoader extends EssentialLoaderBase {
    private static final Logger LOGGER = LogManager.getLogger(EssentialLoader.class);
    private final LoaderInternals loaderInternals = new LoaderInternals();

    public EssentialLoader(Path gameDir, String gameVersion) {
        super(gameDir, gameVersion);
    }

    @Override
    protected void loadPlatform() {
    }

    private ClassLoader getModClassLoader() {
        return this.getClass()
            // gets the class loader set up by stage1 to load stage2 (this class)
            .getClassLoader()
            // gets the class loader set up by stage0 to load stage1
            .getParent()
            // gets the class loader set up by fabric-loader to load stage0 (and mods in general)
            .getParent();
    }

    private void addToClassLoader(final URL url) {
        try {
            this.loaderInternals.addToClassLoaderViaFabricLauncherBase(url);
            return;
        } catch (Throwable t) {
            LOGGER.warn("Failed to add URL to classpath via FabricLauncherBase:", t);
        }

        try {
            this.loaderInternals.addToClassLoaderViaReflection(url);
            return;
        } catch (Throwable t) {
            LOGGER.warn("Failed to add URL to classpath via classloader reflection:", t);
        }

        throw new RuntimeException("Failed to add Essential jar to parent ClassLoader. See preceding exception(s).");
    }

    private void addFakeMod(final Path path, final URL url) throws Exception {
        ModMetadata metadata;
        try (FileSystem fileSystem = FileSystems.newFileSystem(asJar(url.toURI()), Collections.emptyMap())) {
            Path fabricJson = fileSystem.getPath("fabric.mod.json");
            if (!Files.exists(fabricJson)) {
                return; // no fabric.mod.json, nothing we can do
            }
            metadata = this.loaderInternals.parseModMetadata(path, fabricJson);
        }
        this.loaderInternals.injectFakeMod(path, url, metadata);
    }

    @Override
    protected void addToClasspath(final File file) {
        final URL url;
        try {
            url = file.toURI().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        addToClassLoader(url);

        try {
            addFakeMod(file.toPath(), url);
        } catch (Throwable t) {
            LOGGER.warn("Failed to add dummy mod container. Essential will be missing from mod menu.", t);
        }
    }

    @Override
    protected boolean isInClassPath() {
        return this.getModClassLoader().getResource(CLASS_NAME.replace('.', '/') + ".class") != null;
    }

    /**
     * This class provides access to internal of Fabric Loader.
     * As such, any access must be made with utmost care (except errors due to incompatible class changes) and where
     * possible have an appropriate fallback so we do not explode when Fabric Loader's internals change.
     */
    private class LoaderInternals {
        private Class<?> findImplClass(final String name) throws ClassNotFoundException {
            // try newer first cause it may still have deprecated fallback impls for the old classes
            try {
                // fabric loader 0.12
                return Class.forName("net.fabricmc.loader.impl." + name);
            } catch (ClassNotFoundException e) {
                // fabric loader 0.11
                return Class.forName("net.fabricmc.loader." + name);
            }
        }

        private void addToClassLoaderViaFabricLauncherBase(final URL url) {
            FabricLauncherBase.getLauncher().propose(url);
        }

        private void addToClassLoaderViaReflection(final URL url) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
            final ClassLoader classLoader = EssentialLoader.this.getModClassLoader();
            final Method method = classLoader.getClass().getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(classLoader, url);
        }

        private ModMetadata parseModMetadata(final Path modPath, final Path fabricJson) throws Exception {
            Class<?> ModMetadataParser = findImplClass("metadata.ModMetadataParser");
            try {
                // fabric loader 0.11
                return (ModMetadata) ModMetadataParser
                    .getDeclaredMethod("parseMetadata", Logger.class, Path.class)
                    .invoke(null, LOGGER, fabricJson);
            } catch (NoSuchMethodException e) {
                try (InputStream in = Files.newInputStream(fabricJson)) {
                    // fabric loader 0.12
                    return (ModMetadata) ModMetadataParser
                        .getDeclaredMethod("parseMetadata", InputStream.class, String.class, List.class)
                        .invoke(null, in, modPath.toString(), Collections.emptyList());
                }
            }
        }

        @SuppressWarnings("unchecked")
        private void injectFakeMod(final Path path, final URL url, final ModMetadata metadata) throws NoSuchFieldException, IllegalAccessException, NoSuchMethodException, InvocationTargetException, ClassNotFoundException, InstantiationException {
            FabricLoader fabricLoader = FabricLoader.getInstance();
            Class<? extends FabricLoader> fabricLoaderClass = fabricLoader.getClass();
            Class<?> ModContainerImpl;
            try {
                // fabric-loader 0.12
                ModContainerImpl = findImplClass("ModContainerImpl");
            } catch (ClassNotFoundException e) {
                // fabric-loader 0.11
                ModContainerImpl = findImplClass("ModContainer");
            }
            Class<?> LoaderModMetadata = findImplClass("metadata.LoaderModMetadata");
            Class<?> EntrypointMetadata = findImplClass("metadata.EntrypointMetadata");

            Field modMapField = fabricLoaderClass.getDeclaredField("modMap");
            Field modsField = fabricLoaderClass.getDeclaredField("mods");
            Field entrypointStorageField = fabricLoaderClass.getDeclaredField("entrypointStorage");
            Field adapterMapField = fabricLoaderClass.getDeclaredField("adapterMap");

            modMapField.setAccessible(true);
            modsField.setAccessible(true);
            entrypointStorageField.setAccessible(true);
            adapterMapField.setAccessible(true);

            List<Object> mods = (List<Object>) modsField.get(fabricLoader);
            Map<String, Object> modMap = (Map<String, Object>) modMapField.get(fabricLoader);
            Object entrypointStorage = entrypointStorageField.get(fabricLoader);
            Map<String, LanguageAdapter> adapterMap = (Map<String, LanguageAdapter>) adapterMapField.get(fabricLoader);

            // Load the mixin configs
            Method getMixinConfigs = LoaderModMetadata.getDeclaredMethod("getMixinConfigs", EnvType.class);
            for (String mixinConfig : (Collection<String>) getMixinConfigs.invoke(metadata, EnvType.CLIENT)) {
                Mixins.addConfiguration(mixinConfig);
            }

            // Add the mod container
            Object modContainer;
            try {
                modContainer = ModContainerImpl
                    .getConstructor(LoaderModMetadata, URL.class)
                    .newInstance(metadata, url);
            } catch (NoSuchMethodException e) {
                modContainer = ModContainerImpl
                    .getConstructor(LoaderModMetadata, Path.class)
                    .newInstance(metadata, path);
            }
            mods.add(modContainer);
            modMap.put(metadata.getId(), modContainer);

            // Register the entrypoints
            Method addMethod = entrypointStorage.getClass()
                .getDeclaredMethod("add", ModContainerImpl, String.class, EntrypointMetadata, Map.class);
            addMethod.setAccessible(true);
            Method getEntrypointKeys = LoaderModMetadata.getDeclaredMethod("getEntrypointKeys");
            Method getEntrypoints = LoaderModMetadata.getDeclaredMethod("getEntrypoints", String.class);
            for (String key : (Collection<String>) getEntrypointKeys.invoke(metadata)) {
                for (Object entrypointMetadata : (List<Object>) getEntrypoints.invoke(metadata, key)) {
                    addMethod.invoke(entrypointStorage, modContainer, key, entrypointMetadata, adapterMap);
                }
            }
        }
    }
}
