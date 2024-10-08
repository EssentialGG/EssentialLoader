package gg.essential.loader.stage2;

import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import gg.essential.loader.stage2.data.ModJarMetadata;
import gg.essential.loader.stage2.jij.JarInJarDependenciesHandler;
import gg.essential.loader.stage2.restart.ForkedNeedsRestartUI;
import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.fabricmc.loader.launch.common.FabricLauncherBase;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

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
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public class EssentialLoader extends EssentialLoaderBase {
    private static final Logger LOGGER = LogManager.getLogger(EssentialLoader.class);
    private final LoaderInternals loaderInternals = new LoaderInternals();

    public EssentialLoader(Path gameDir, String gameVersion) {
        super(gameDir, gameVersion);

        String debugLog = System.getProperty("essential.debuglog");
        if (debugLog != null) {
            Level level;
            if (debugLog.equals("true")) {
                level = Level.DEBUG;
            } else {
                level = Level.getLevel(debugLog.toUpperCase(Locale.ROOT));
                if (level == null) {
                    throw new IllegalArgumentException("Unknown log level \"" + debugLog + "\"");
                }
            }
            Log4j2Hacks.addDebugLogFile(level);
        }
    }

    @Override
    protected void loadPlatform() {
    }

    @Override
    protected ClassLoader getModClassLoader() {
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

    private ModMetadata parseMetadata(final Path path) throws Exception {
        try (FileSystem fileSystem = FileSystems.newFileSystem(asJar(path.toUri()), Collections.emptyMap())) {
            Path fabricJson = fileSystem.getPath("fabric.mod.json");
            if (!Files.exists(fabricJson)) {
                return null; // no fabric.mod.json, nothing we can do
            }
            return this.loaderInternals.parseModMetadata(path, fabricJson);
        }
    }

    private void addFakeMod(final Path path, final URL url) throws Exception {
        ModMetadata metadata = parseMetadata(path);
        this.loaderInternals.injectFakeMod(path, url, metadata);
    }

    @Override
    protected void addToClasspath(Mod mod, ModJarMetadata jarMeta, Path mainJar, List<Path> innerJars) {
        // FIXME doing this mod-by-mod may require a restart per mod; naively sharing the same handler between all mods
        //       may cause conflicts between them. we should probably implement a proper solution before prime time.
        //       we also need to black-list our stage0
        JarInJarDependenciesHandler jijHandler = new JarInJarDependenciesHandler(getExtractedJarsRoot(mod));

        innerJars = innerJars.stream()
            .flatMap(path -> jijHandler.loadMod(path).stream())
            .collect(Collectors.toCollection(ArrayList::new));

        if (!jijHandler.complete()) {
            ForkedNeedsRestartUI ui = new ForkedNeedsRestartUI(jijHandler.getUpdatedModNames(), jijHandler.getModsToDisable());
            ui.show();
            ui.waitForClose();
            ui.exit();
            return;
        }

        super.addToClasspath(mod, jarMeta, mainJar, innerJars);
    }

    @Override
    protected void addToClasspath(Path path) {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            try {
                RuntimeModRemapper runtimeModRemapper = new RuntimeModRemapper(loaderInternals);
                path = runtimeModRemapper.remap(path, parseMetadata(path));
            } catch (Exception e) {
                throw new RuntimeException("Failed to remap Essential to dev mappings", e);
            }
        }

        final URL url;
        try {
            url = path.toUri().toURL();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        addToClassLoader(url);

        try {
            addFakeMod(path, url);
        } catch (Throwable t) {
            LOGGER.warn("Failed to add dummy mod container. Essential will be missing from mod menu.", t);
        }
    }

    @Override
    protected void doInitialize() {
        super.doInitialize();

        try {
            chainLoadMixins();
        } catch (Throwable t) {
            LOGGER.error("Failed to load mixin configs:", t);
        }
    }

    // Usually our mixins are chain-loaded just fine. Mixin will load newly added configs every time a new class is
    // loaded until the first mixin is actually applied.
    // It is however possible, even though strongly discouraged, that a third-party preLaunch entrypoint starts loading
    // MC classes (likely unintentional, e.g. https://github.com/ejektaflex/Kambrik/issues/7) which are targeted by
    // fourth-party mixins (in the aforementioned case, fabric-api mixins) before we get to register our mixins.
    // In that case, our mixins will not be loaded and stuff would break.
    // In a desperate last attempt to save what can be saved, we will go and reflect right into Mixin's inners to take
    // the loading into our own hands.
    // This will fail if Mixin changes (we'll just have to adapt then), or if the classes we want to mixin into
    // have already been loaded (though in that case there's nothing we can do except getting the third-party mod
    // fixed). But that is fine because this is not the default path, so only a small hand of users will be affected.
    private void chainLoadMixins() throws ReflectiveOperationException {
        if (Mixins.getUnvisitedCount() == 0) {
            return; // nothing to do, Mixin already chain-loaded our config by itself
        }

        MixinEnvironment environment = MixinEnvironment.getDefaultEnvironment();
        Object transformer = environment.getActiveTransformer();
        Field processorField = transformer.getClass().getDeclaredField("processor");
        processorField.setAccessible(true);
        Object processor = processorField.get(transformer);
        Method select = processor.getClass().getDeclaredMethod("select", MixinEnvironment.class);
        select.setAccessible(true);
        select.invoke(processor, environment);
    }

    /**
     * This class provides access to the internals of Fabric Loader.
     * As such, any access must be made with utmost care (expect errors due to incompatible class changes) and where
     * possible have an appropriate fallback so that we do not explode when Fabric Loader's internals change.
     */
    public class LoaderInternals {
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
            if (classLoader == null) {
                throw new IllegalStateException("Failed to traverse class loader hierarchy to find mod class loader.");
            }
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
                    try {
                        // fabric loader 0.12
                        return (ModMetadata) ModMetadataParser
                            .getDeclaredMethod("parseMetadata", InputStream.class, String.class, List.class)
                            .invoke(null, in, modPath.toString(), Collections.emptyList());
                    } catch (NoSuchMethodException e1) {
                        try {
                            // fabric loader 0.14
                            Class<?> VersionOverrides = findImplClass("metadata.VersionOverrides");
                            Class<?> DependencyOverrides = findImplClass("metadata.DependencyOverrides");
                            Object versionOverrides = VersionOverrides.getConstructor().newInstance();
                            Object dependencyOverrides = DependencyOverrides.getConstructor(Path.class).newInstance(Paths.get("_invalid_"));
                            return (ModMetadata) ModMetadataParser
                                .getDeclaredMethod("parseMetadata", InputStream.class, String.class, List.class, VersionOverrides, DependencyOverrides)
                                .invoke(null, in, modPath.toString(), Collections.emptyList(), versionOverrides, dependencyOverrides);
                        } catch (NoSuchMethodException e2) {
                            // fabric loader 0.14.11
                            Class<?> VersionOverrides = findImplClass("metadata.VersionOverrides");
                            Class<?> DependencyOverrides = findImplClass("metadata.DependencyOverrides");
                            Object versionOverrides = VersionOverrides.getConstructor().newInstance();
                            Object dependencyOverrides = DependencyOverrides.getConstructor(Path.class).newInstance(Paths.get("_invalid_"));
                            return (ModMetadata) ModMetadataParser
                                .getDeclaredMethod("parseMetadata", InputStream.class, String.class, List.class, VersionOverrides, DependencyOverrides, Boolean.TYPE)
                                .invoke(null, in, modPath.toString(), Collections.emptyList(), versionOverrides, dependencyOverrides, FabricLoader.getInstance().isDevelopmentEnvironment());
                        }
                    }
                }
            }
        }

        @SuppressWarnings("UnstableApiUsage")
        public void remapMod(ModMetadata metadata, Path inputPath, Path outputPath) throws Exception {
            Class<?> ModCandidate;
            try {
                // fabric-loader 0.16.1
                ModCandidate = findImplClass("discovery.ModCandidateImpl");
            } catch (ClassNotFoundException e) {
                // fabric-loader 0.16.0
                ModCandidate = findImplClass("discovery.ModCandidate");
            }
            Class<?> ModResolver = findImplClass("discovery.ModResolver");
            Class<?> RuntimeModRemapper = findImplClass("discovery.RuntimeModRemapper");

            Object candidate = createCandidate(inputPath, inputPath.toUri().toURL(), metadata);

            try {
                // fabric loader 0.11
                Method getInMemoryFs = ModResolver.getDeclaredMethod("getInMemoryFs");
                Method remap = RuntimeModRemapper.getDeclaredMethod("remap", Collection.class, FileSystem.class);
                Method getOriginUrl = ModCandidate.getDeclaredMethod("getOriginUrl");

                FileSystem fileSystem = (FileSystem) getInMemoryFs.invoke(null);

                Object result = remap.invoke(null, Collections.singleton(candidate), fileSystem);
                Object remappedCandidate = ((Collection<?>) result).iterator().next();
                URL remappedUrl = (URL) getOriginUrl.invoke(remappedCandidate);

                try (InputStream in = remappedUrl.openStream()) {
                    Files.copy(in, outputPath);
                }
            } catch (NoSuchMethodException e) {
                // fabric loader 0.12
                Method remap = RuntimeModRemapper.getDeclaredMethod("remap", Collection.class, Path.class, Path.class);

                Path tmpDir = Files.createTempDirectory("remap-tmp");
                Path outDir = Files.createTempDirectory("remap-out");
                try {
                    remap.invoke(null, Collections.singleton(candidate), tmpDir, outDir);
                    Path resultPath;
                    try {
                        // fabric loader 0.12
                        Method getPath = ModCandidate.getDeclaredMethod("getPath");
                        resultPath = (Path) getPath.invoke(candidate);
                    } catch (NoSuchMethodException e1) {
                        // fabric loader 0.13
                        Method getPaths = ModCandidate.getDeclaredMethod("getPaths");
                        @SuppressWarnings("unchecked")
                        List<Path> paths = (List<Path>) getPaths.invoke(candidate);
                        resultPath = paths.get(0);
                    }
                    Files.move(resultPath, outputPath);
                } finally {
                    MoreFiles.deleteRecursively(tmpDir, RecursiveDeleteOption.ALLOW_INSECURE);
                    MoreFiles.deleteRecursively(outDir, RecursiveDeleteOption.ALLOW_INSECURE);
                }
            }
        }

        private Object createCandidate(Path path, URL url, Object metadata) throws ClassNotFoundException, InvocationTargetException, InstantiationException, IllegalAccessException, NoSuchMethodException {
            Class<?> LoaderModMetadata = findImplClass("metadata.LoaderModMetadata");
            Class<?> ModCandidate;
            try {
                // fabric-loader 0.16.1
                ModCandidate = findImplClass("discovery.ModCandidateImpl");
            } catch (ClassNotFoundException e) {
                // fabric-loader 0.16.0
                ModCandidate = findImplClass("discovery.ModCandidate");
            }
            try {
                // fabric loader 0.11
                return ModCandidate.getConstructor(LoaderModMetadata, URL.class, int.class, boolean.class)
                    .newInstance(metadata, url, /* depth */ 0, /* needsRemap */ true);
            } catch (NoSuchMethodException e) {
                try {
                    // fabric loader 0.12
                    Method createCandidate = ModCandidate.getDeclaredMethod("createPlain", Path.class, LoaderModMetadata, boolean.class, Collection.class);
                    createCandidate.setAccessible(true);
                    return createCandidate.invoke(null, path, metadata, /* needsRemap */ true, /* nestedMods */ Collections.emptyList());
                } catch (NoSuchMethodException e1) {
                    // fabric loader 0.13
                    Method createCandidate = ModCandidate.getDeclaredMethod("createPlain", List.class, LoaderModMetadata, boolean.class, Collection.class);
                    createCandidate.setAccessible(true);
                    return createCandidate.invoke(null, Collections.singletonList(path), metadata, /* needsRemap */ true, /* nestedMods */ Collections.emptyList());
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
                // fabric-loader 0.12.2
                modContainer = ModContainerImpl
                    .getConstructor(LoaderModMetadata, URL.class)
                    .newInstance(metadata, url);
            } catch (NoSuchMethodException e) {
                try {
                    // fabric-loader 0.12.3
                    modContainer = ModContainerImpl
                        .getConstructor(LoaderModMetadata, Path.class)
                        .newInstance(metadata, path);
                } catch (NoSuchMethodException e1) {
                    // fabric-loader 0.13
                    Object modCandidate = createCandidate(path, url, metadata);
                    modContainer = ModContainerImpl
                        .getConstructor(modCandidate.getClass())
                        .newInstance(modCandidate);
                }
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
            // FIXME need to chain-load prelaunch entrypoint for mods that need it because that one has already been
            //       (or rather, is currently being) invoked
        }
    }
}
