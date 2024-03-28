package gg.essential.loader.stage2;

import cpw.mods.modlauncher.LaunchPluginHandler;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.TypesafeMap;
import cpw.mods.modlauncher.serviceapi.ILaunchPluginService;
import gg.essential.loader.stage2.util.UnsafeHacks;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.LanguageLoadingProvider;
import net.minecraftforge.fml.loading.ModDirTransformerDiscoverer;
import net.minecraftforge.fml.loading.SortedLanguageLoadingProvider;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.forgespi.locating.IModFile;
import org.jetbrains.annotations.NotNull;

import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EssentialTransformationService implements ITransformationService {
    private static final Supplier<TypesafeMap.Key<List<Path>>> JARS_KEY = IEnvironment.buildKey("essential.stage2._jars", List.class);

    /**
     * Essential JiJ files matching this pattern are injected into the language class loader instead of being injected
     * as regular mods. This is used to override old Kotlin versions in KotlinForForge with newer ones.
     */
    private static final Pattern JIJ_KOTLIN_FILES = Pattern.compile("kotlinx?-([a-z0-9-]+)-(\\d+\\.\\d+\\.\\d+)\\.jar");

    private final List<Path> jars = new ArrayList<>();

    public void addToClasspath(final Path path) {
        this.jars.add(path);
    }

    @Override
    public @NotNull String name() {
        return "essential-loader";
    }

    @Override
    public void initialize(IEnvironment environment) {
        // Register our own jar (stage2) as containing an extra mod locator, that will then inject our jars when loaded
        ModDirTransformerDiscoverer.getExtraLocators().add(getStage2JarPath());
        // Mod locators get loaded in a dedicated class loader, so we need some way to communicate our jars to it
        environment.computePropertyIfAbsent(JARS_KEY.get(), __ -> jars);

        // Register ourselves as a launch plugin, so we get a chance to mess with the TransformingClassLoader before it
        // starts loading classes
        try {
            registerLaunchPlugin(new EssentialLaunchPluginService());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
    }

    @SuppressWarnings("rawtypes")
    @Override
    public @NotNull List<ITransformer> transformers() {
        return Collections.emptyList();
    }

    @Override
    public void beginScanning(IEnvironment environment) {
    }

    private static Path getStage2JarPath() {
        try {
            return Paths.get(EssentialTransformationService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e); // shouldn't happen because our stage1 is in charge of loading stage2
        }
    }

    private static void registerLaunchPlugin(ILaunchPluginService plugin) {
        UnsafeHacks.Accessor<Launcher, LaunchPluginHandler> launchPluginsField =
            UnsafeHacks.makeAccessor(Launcher.class, "launchPlugins");
        UnsafeHacks.Accessor<LaunchPluginHandler, Map<String, ILaunchPluginService>> pluginsField =
            UnsafeHacks.makeAccessor(LaunchPluginHandler.class, "plugins");

        pluginsField.update(launchPluginsField.get(Launcher.INSTANCE), pluginsMap -> {
            // We want our plugin to run first (in case other plugins like Mixin start looking at classes), so we need to
            // copy the map into a linked hash map to get predictable iteration order.
            Map<String, ILaunchPluginService> pluginsLinkedMap = new LinkedHashMap<>();
            pluginsLinkedMap.put(plugin.name(), plugin);
            pluginsLinkedMap.putAll(pluginsMap);
            return pluginsLinkedMap;
        });
    }

    public static class ModLocator extends AbstractJarFileLocator {
        public ModLocator() {
            // Forge's LanguageLoadingProvider is initialized from FML's ITransformationService during `initialize` (via
            // `FMLLoader.initialize`) and is then first used during `runScan` (via `FMLLoader.beginRunScan` and
            // `ModDiscoverer.discoverMods`).
            // We need to hijack it somewhere between those two. And this constructor happens to be running at one such
            // point (during `runScan` but before `discoverMods`, in the constructor of `ModDiscoverer`).
            UnsafeHacks.<FMLLoader, LanguageLoadingProvider>makeAccessor(FMLLoader.class, "languageLoadingProvider")
                .update(null, oldProvider -> {
                    SortedLanguageLoadingProvider newProvider = UnsafeHacks.allocateCopy(oldProvider, SortedLanguageLoadingProvider.class);
                    newProvider.extraHighPriorityFiles = getJars(true).collect(Collectors.toList());
                    return newProvider;
                });

            // TODO upgrading of third-party (non-language) mods
        }

        private Stream<Path> getJars(boolean kotlin) {
            return Launcher.INSTANCE.environment()
                .getProperty(JARS_KEY.get())
                .orElseThrow(IllegalStateException::new)
                .stream()
                .filter(it -> JIJ_KOTLIN_FILES.matcher(it.getFileName().toString()).matches() == kotlin);
        }

        @Override
        public List<IModFile> scanMods() {
            return getJars(false)
                .map(path -> ModFile.newFMLInstance(path, this))
                .peek(modFile -> modJars.computeIfAbsent(modFile, this::createFileSystem))
                .collect(Collectors.toList());
        }

        @Override
        public String name() {
            return "essential-loader";
        }

        @Override
        public void initArguments(Map<String, ?> arguments) {
        }
    }
}
