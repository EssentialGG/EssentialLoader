package gg.essential.loader.stage1;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.NamedPath;
import cpw.mods.modlauncher.api.TypesafeMap;
import gg.essential.loader.stage1.util.DelegatingTransformationService;
import gg.essential.loader.stage1.util.FallbackTransformationService;
import net.minecraftforge.fml.loading.ModDirTransformerDiscoverer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class EssentialTransformationService extends DelegatingTransformationService {
    private static final Logger LOGGER = LogManager.getLogger(EssentialTransformationService.class);
    private static final String KEY_LOADED = "gg.essential.loader.stage1.loaded";

    public EssentialTransformationService(final ITransformationService stage0) throws Exception {
        super(new FallbackTransformationService(findUniqueId(stage0)));

        // Check if another transformation service has already loaded stage2 (we do not want to load it twice)
        final TypesafeMap blackboard = Launcher.INSTANCE.blackboard();
        final TypesafeMap.Key<ITransformationService> LOADED =
            TypesafeMap.Key.getOrCreate(blackboard, KEY_LOADED, ITransformationService.class);
        if (blackboard.get(LOADED).isPresent()) {
            return;
        }
        // We are doing it
        blackboard.computeIfAbsent(LOADED, __ -> this);

        final Path gameDir = Launcher.INSTANCE.environment()
            .getProperty(IEnvironment.Keys.GAMEDIR.get())
            .orElse(Path.of("."));

        // At this point modlauncher does not yet expose the Minecraft version to us. So instead we will just assume
        // the first one we support (stage2 will be the same for all modlauncher versions anyway) and once we are in
        // stage2, we can re-query at a later point as needed (but we always retain the ability to run an up-to-date
        // stage2 as early as possible).
        final String mcVersion = "1.17.1";

        final EssentialLoader loader = EssentialLoader.getInstance("forge_" + mcVersion);
        loader.load(gameDir);

        Object stage2 = loader.getStage2();
        if (stage2 != null) {
            this.delegate = (ITransformationService) stage2.getClass()
                .getDeclaredMethod("getTransformationService")
                .invoke(stage2);
        }

        final List<Path> sourceFiles = getSourceFiles(stage0.getClass());
        if (sourceFiles.isEmpty()) {
            LOGGER.error("Not able to determine current file. Mod will NOT work");
            return;
        }
        for (Path sourceFile : sourceFiles) {
            setupSourceFile(sourceFile);
        }
    }

    private static String findUniqueId(ITransformationService stage0) {
        String[] partsArray = stage0.getClass().getName().split("\\.");
        List<String> standardParts = List.of("gg", "essential", "loader", "stage0", "EssentialTransformationService");

        Deque<String> actualStack = new ArrayDeque<>(Arrays.asList(partsArray));
        Deque<String> standardStack = new ArrayDeque<>(standardParts);

        // Remove common suffix
        while (!actualStack.isEmpty() && !standardStack.isEmpty()) {
            if (actualStack.getLast().equals(standardStack.getLast())) {
                actualStack.removeLast();
                standardStack.removeLast();
            } else {
                break;
            }
        }

        // Build unique id consisting of remaining package parts
        String uniqueId = actualStack.stream()
            .map(it -> it.toLowerCase(Locale.ROOT))
            .collect(Collectors.joining("-"));

        if (uniqueId.isEmpty()) {
            uniqueId = "stage0"; // fallback for the non-relocated stage0 (if forge ever implements JiJ)
        }

        return "essential-loader-" + uniqueId;
    }

    @SuppressWarnings("unchecked")
    private void setupSourceFile(final Path sourceFile) throws Exception {
        final Path normalizedSourceFile = sourceFile.normalize();

        // Forge will by default ignore a mod file if it contains an implementation of ITransformationService
        // So we need to remove ourselves from that exclusion list
        Field foundField = ModDirTransformerDiscoverer.class.getDeclaredField("found");
        foundField.setAccessible(true);
        ((List<NamedPath>) foundField.get(null)).removeIf(namedPath ->
            Arrays.stream(namedPath.paths()).anyMatch(path -> path.normalize().equals(normalizedSourceFile)));
    }

    private List<Path> getSourceFiles(Class<?> stage0Class) {
        return stage0Class.getModule()
            .getLayer()
            .configuration()
            .findModule(stage0Class.getModule().getName())
            .flatMap(rm -> rm.reference().location())
            .map(Path::of)
            .stream()
            .collect(Collectors.toList());
    }
}
