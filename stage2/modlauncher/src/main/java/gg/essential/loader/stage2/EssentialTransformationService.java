package gg.essential.loader.stage2;

import cpw.mods.jarhandling.SecureJar;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.fml.loading.moddiscovery.ModValidator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class EssentialTransformationService implements ITransformationService {
    private static final Logger LOGGER = LogManager.getLogger(EssentialTransformationService.class);

    private final ModLocator modLocator = new ModLocator();
    private final List<Path> jars = new ArrayList<>();
    private boolean modsInjected;

    public void addToClasspath(final File file) {
        this.jars.add(file.toPath());
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

            for (Path jar : this.jars) {
                ModFile modFile = ModFile.newFMLInstance(this.modLocator, jar);
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
        return List.of();
    }

    @Override
    public List<Resource> completeScan(IModuleLayerManager layerManager) {
        if (!modsInjected && injectMods()) {
            modsInjected = true;
        }
        if (!modsInjected) {
            LOGGER.error("Failed to inject Essential into Forge mod list, falling back to Mixin-only operation. " +
                "Mod will not be listed in Forge's mod list.");
            List<SecureJar> secureJars = this.jars.stream().map(SecureJar::from).collect(Collectors.toList());
            return Collections.singletonList(new Resource(IModuleLayerManager.Layer.GAME, secureJars));
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
