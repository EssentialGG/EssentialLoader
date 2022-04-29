package gg.essential.loader.stage2;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.TypesafeMap;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class EssentialTransformationService implements ITransformationService {
    private static final Supplier<TypesafeMap.Key<List<Path>>> JARS_KEY = IEnvironment.buildKey("essential.stage2._jars", List.class);

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

    public static class ModLocator extends AbstractJarFileLocator {
        public ModLocator() {
            // Forge's LanguageLoadingProvider is initialized from FML's ITransformationService during `initialize` (via
            // `FMLLoader.initialize`) and is then first used during `runScan` (via `FMLLoader.beginRunScan` and
            // `ModDiscoverer.discoverMods`).
            // We need to hijack it somewhere between those two. And this constructor happens to be running at one such
            // point (during `runScan` but before `discoverMods`, in the constructor of `ModDiscoverer`).
            UnsafeHacks.<FMLLoader, LanguageLoadingProvider>makeAccessor(FMLLoader.class, "languageLoadingProvider")
                .update(null, oldProvider -> UnsafeHacks.allocateCopy(oldProvider, SortedLanguageLoadingProvider.class));

            // TODO upgrading of third-party (non-language) mods
        }

        @Override
        public List<IModFile> scanMods() {
            return Launcher.INSTANCE.environment()
                .getProperty(JARS_KEY.get())
                .orElseThrow(IllegalStateException::new)
                .stream()
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
