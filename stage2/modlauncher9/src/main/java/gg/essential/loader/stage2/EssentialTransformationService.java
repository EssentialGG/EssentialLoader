package gg.essential.loader.stage2;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.*;
import gg.essential.loader.stage2.modlauncher.CompatibilityLayer;
import gg.essential.loader.stage2.modlauncher.EssentialModLocator;
import gg.essential.loader.stage2.util.KFFMerger;
import gg.essential.loader.stage2.util.SortedJarOrPathList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static gg.essential.loader.stage2.Utils.hasClass;

public class EssentialTransformationService implements ITransformationService {
    private static final Logger LOGGER = LogManager.getLogger(EssentialTransformationService.class);
    private static final Map<String, String> COMPATIBILITY_IMPLEMENTATIONS = Map.of(
            "9.", "ML9CompatibilityLayer",
            "10.", "ML10CompatibilityLayer"
    );
    private static CompatibilityLayer compatibilityLayer;

    private final Path gameDir;
    private final List<SecureJar> pluginJars = new ArrayList<>();
    private final List<SecureJar> gameJars = new ArrayList<>();
    private final KFFMerger kffMerger = new KFFMerger();
    private EssentialModLocator modLocator;
    private boolean modsInjected;

    public EssentialTransformationService(Path gameDir) {
        this.gameDir = gameDir;
    }

    public void addToClasspath(final Path path) {
        final SecureJar jar = SecureJar.from(j -> new SelfRenamingJarMetadata(j, path, determineLayer(j)), path);
        if (this.kffMerger.addKotlinJar(path, jar)) {
            return;
        }
        if (determineLayer(jar) == IModuleLayerManager.Layer.PLUGIN) {
            this.pluginJars.add(jar);
        } else {
            this.gameJars.add(jar);
        }
    }

    private static IModuleLayerManager.Layer determineLayer(SecureJar jar) {
        final String modType = compatibilityLayer.getManifest(jar).getMainAttributes().getValue("FMLModType");
        if (IModFile.Type.LANGPROVIDER.name().equals(modType) || IModFile.Type.LIBRARY.name().equals(modType)) {
            return IModuleLayerManager.Layer.PLUGIN;
        } else {
            return IModuleLayerManager.Layer.GAME;
        }
    }

    @Override
    public @NotNull String name() {
        return "essential-loader";
    }

    @Override
    public void initialize(IEnvironment environment) {
        String modLauncherVersion = environment.getProperty(IEnvironment.Keys.MLIMPL_VERSION.get()).orElseThrow();
        compatibilityLayer = findCompatibilityLayerImpl(modLauncherVersion);
        modLocator = findModLocatorImpl();
    }

    @SuppressWarnings("unchecked")
    private CompatibilityLayer findCompatibilityLayerImpl(String mlVersion) {
        String implementation = null;
        for (Map.Entry<String, String> entry : COMPATIBILITY_IMPLEMENTATIONS.entrySet()) {
            if (mlVersion.startsWith(entry.getKey())) {
                implementation = entry.getValue();
                break;
            }
        }
        if (implementation == null) {
            throw new UnsupportedOperationException("Unable to find a matching compatibility layer for ModLauncher version " + mlVersion);
        }
        try {
            Class<? extends CompatibilityLayer> clazz = (Class<? extends CompatibilityLayer>) Class.forName("gg.essential.loader.stage2.modlauncher." + implementation);
            return clazz.getConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static EssentialModLocator findModLocatorImpl() {
        String version;
        if (hasClass("net.minecraftforge.forgespi.locating.IModLocator$ModFileOrException")) {
            if (!hasClass("net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator")) {
                version = "49_0_38";
            } else {
                version = "41_0_34";
            }
        } else {
            if (hasClass("net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator")) {
                version = "40_1_60";
            } else {
                version = "37_0_0";
            }
        }
        try {
            String clsName = "gg.essential.loader.stage2.modlauncher.Forge_" + version + "_ModLocator";
            return (EssentialModLocator) Class.forName(clsName)
                .getDeclaredConstructor()
                .newInstance();
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
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

    /**
     * By default, if there are multiple jars declaring the same module in a layer, ModLauncher will simply pick
     * whichever was registered first (see JarModuleFinder). Registration order is effectively random (HashMap iteration
     * order), so it effectively picks a random version, which is no good.
     * To work around this behavior, we replace the list which holds all jars in a layer with one that automatically
     * sorts by version.
     * This may fail if ModLauncher internals change but there isn't much we can do about it. In such case, we will
     * simply fall back to the old, unstable behavior.
     */
    private void configureLayerToBeSortedByVersion(IModuleLayerManager.Layer layer) {
        try {
            IModuleLayerManager layerManager = Launcher.INSTANCE.findLayerManager().orElseThrow();
            Field layersField = layerManager.getClass().getDeclaredField("layers");
            layersField.setAccessible(true);
            @SuppressWarnings("unchecked")
            Map<IModuleLayerManager.Layer, List<Object>> layers =
                (Map<IModuleLayerManager.Layer, List<Object>>) layersField.get(layerManager);

            layers.compute(layer, (__, list) -> {
                SortedJarOrPathList sortedList = new SortedJarOrPathList(kffMerger::maybeMergeInto);
                if (list != null) {
                    sortedList.addAll(list);
                }
                return sortedList;
            });
        } catch (Throwable t) {
            LOGGER.error("Failed to replace mod list of " + layer + " with sorted list:", t);
        }
    }

    /**
     * We inject our mod into the ModValidator's candidate list before it starts scanning all jars in parallel (or put
     * another way, between the calls to its stage 1 validation and its stage 2 validation methods).
     * The window of opportunity is somewhere between FMLLoader's beginScanning (where the modValidator is created)
     * and its completeScanning (where parallel scanning is started). Since we cannot know whether our transformation
     * service or FML is called first, we simply try both methods, one of which should be at the right moment.
     *
     * If this fails (e.g. because Forge internals change), then we fall back to simply adding our jar to the game layer
     * directly, which will still allow Mixin to pick it up, and therefore it will still mostly function correctly (but
     * it won't show in the mods menu).
     */
    private boolean injectMods() {
        try {
            return modLocator.injectMods(gameJars);
        } catch (Throwable e) {
            LOGGER.error("Error injecting into mod list:", e);
            return false;
        }
    }

    @Override
    public List<Resource> beginScanning(IEnvironment environment) {
        // Forge makes available the MC version in its `initialize` method, the first of our methods which we can be
        // sure is called after Forge's method is this one, so this is the earliest point at which we can load essential
        // proper.
        String mcVersion = "forge_" + FMLLoader.versionInfo().mcVersion();
        ActualEssentialLoader essentialLoader = new ActualEssentialLoader(gameDir, mcVersion, this);
        try {
            essentialLoader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (injectMods()) {
            modsInjected = true;
        }
        configureLayerToBeSortedByVersion(IModuleLayerManager.Layer.PLUGIN);
        return List.of(new Resource(IModuleLayerManager.Layer.PLUGIN, this.pluginJars));
    }

    @Override
    public List<Resource> completeScan(IModuleLayerManager layerManager) {
        if (!modsInjected && injectMods()) {
            modsInjected = true;
        }
        if (!modsInjected) {
            LOGGER.error("Failed to inject Essential into Forge mod list, falling back to Mixin-only operation. " +
                "Mod will not be listed in Forge's mod list.");
            configureLayerToBeSortedByVersion(IModuleLayerManager.Layer.GAME);
            return Collections.singletonList(new Resource(IModuleLayerManager.Layer.GAME, this.gameJars));
        }
        return List.of();
    }
}
