package gg.essential.loader.stage2;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import gg.essential.loader.stage2.util.SortedJarOrPathList;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModValidator;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class EssentialTransformationService implements ITransformationService {
    private static final Logger LOGGER = LogManager.getLogger(EssentialTransformationService.class);

    private final ModLocator modLocator = new ModLocator();
    private final List<SecureJar> pluginJars = new ArrayList<>();
    private final List<SecureJar> gameJars = new ArrayList<>();
    private boolean modsInjected;

    public void addToClasspath(final Path path) {
        final SecureJar jar = SecureJar.from(j -> new SelfRenamingJarMetadata(j, path, determineLayer(j)), path);
        if (determineLayer(jar) == IModuleLayerManager.Layer.PLUGIN) {
            this.pluginJars.add(jar);
        } else {
            this.gameJars.add(jar);
        }
    }

    private static IModuleLayerManager.Layer determineLayer(SecureJar jar) {
        final String modType = jar.getManifest().getMainAttributes().getValue("FMLModType");
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
     * To workaround this behavior, we replace the list which holds all jars in a layer with one that automatically
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
                SortedJarOrPathList sortedList = new SortedJarOrPathList();
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
     * directly, which will still allow Mixin to pick it up and therefore it will still mostly function correctly (but
     * it won't show in the mods menu).
     */
    private boolean injectMods() {
        try {
            Field modValidatorField = FMLLoader.class.getDeclaredField("modValidator");
            modValidatorField.setAccessible(true);
            ModValidator modValidator = (ModValidator) modValidatorField.get(null);

            if (modValidator == null) {
                return false;
            }

            Field candidateModsField = ModValidator.class.getDeclaredField("candidateMods");
            candidateModsField.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<ModFile> modFiles = (List<ModFile>) candidateModsField.get(modValidator);

            for (SecureJar jar : this.gameJars) {
                ModFile modFile = ModFile.newFMLInstance(this.modLocator, jar.getPrimaryPath());
                modFile.identifyMods();
                modFiles.add(modFile);
            }

            return true;
        } catch (ReflectiveOperationException e) {
            LOGGER.error("Error injecting into mod list:", e);
            return false;
        }
    }

    @Override
    public List<Resource> beginScanning(IEnvironment environment) {
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

    private static class ModLocator extends AbstractJarFileLocator {
        @Override
        public Stream<Path> scanCandidates() {
            return Stream.of();
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
