package gg.essential.loader.stage1;

import cpw.mods.modlauncher.api.ITransformationService;
import gg.essential.loader.stage1.util.FallbackTransformationService;
import gg.essential.loader.stage1.util.IDelegatingTransformationService;
import net.minecraftforge.fml.loading.ModDirTransformerDiscoverer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("unused")
public class EssentialTransformationService extends EssentialTransformationServiceBase implements IDelegatingTransformationService {
    private static final Logger LOGGER = LogManager.getLogger(EssentialTransformationServiceBase.class);
    private static final String KEY_LOADED = "gg.essential.loader.stage1.loaded";

    // At this point modlauncher does not yet expose the Minecraft version to us. So instead we will just assume
    // the first one we support (stage2 will be the same for all MC versions on the same stage1 anyway) and once we are
    // in stage2, we can re-query at a later point as needed (but we always retain the ability to run an up-to-date
    // stage2 as early as possible).
    private static final String MC_VERSION = "1.16.5";

    public EssentialTransformationService(final ITransformationService stage0) throws Exception {
        super(stage0, FallbackTransformationService::new, "modlauncher8", MC_VERSION);

        final List<Path> sourceFiles = getSourceFiles(stage0.getClass());
        if (sourceFiles.isEmpty()) {
            LOGGER.error("Not able to determine current file. Mod will NOT work");
            return;
        }
        for (Path sourceFile : sourceFiles) {
            setupSourceFile(sourceFile);
        }
    }

    @SuppressWarnings("unchecked")
    private void setupSourceFile(final Path sourceFile) throws Exception {
        final Path normalizedSourceFile = sourceFile.normalize();

        // Forge will by default ignore a mod file if it contains an implementation of ITransformationService
        // So we need to remove ourselves from that exclusion list
        Field foundField = ModDirTransformerDiscoverer.class.getDeclaredField("transformers");
        foundField.setAccessible(true);
        ((List<Path>) foundField.get(null)).removeIf(path -> path.normalize().equals(normalizedSourceFile));
    }


    private List<Path> getSourceFiles(Class<?> stage0Class) {
        String stage0ClassPath = stage0Class.getName().replace('.', '/') + ".class";
        List<Path> sourceFiles = new ArrayList<>();
        for (Path path : ModDirTransformerDiscoverer.allExcluded()) {
            try (FileSystem fileSystem = FileSystems.newFileSystem(path, (ClassLoader) null)) {
                if (Files.exists(fileSystem.getPath(stage0ClassPath))) {
                    sourceFiles.add(path);
                }
            } catch (Exception e) {
                LOGGER.error("Failed to check for stage0 class in " + path + ":", e);
            }
        }
        return sourceFiles;
    }
}
