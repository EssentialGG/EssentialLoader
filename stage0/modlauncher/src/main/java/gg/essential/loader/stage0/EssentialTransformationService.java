package gg.essential.loader.stage0;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import gg.essential.loader.stage0.util.DelegatingTransformationService;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static gg.essential.loader.stage0.EssentialLoader.STAGE1_PKG;

public class EssentialTransformationService extends DelegatingTransformationService {
    private static final String STAGE1_CLS = STAGE1_PKG + "EssentialTransformationService";

    public EssentialTransformationService() {
        super(EssentialTransformationService::loadStage1OrThrow);
    }

    private static ITransformationService loadStage1OrThrow(ITransformationService stage0) {
        try {
            return loadStage1(stage0);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ITransformationService loadStage1(ITransformationService stage0) throws Exception {
        Path gameDir = Launcher.INSTANCE.environment()
            .getProperty(IEnvironment.Keys.GAMEDIR.get())
            .orElseGet(() -> Path.of("."));

        // Extract/update stage1 from embedded jars
        final EssentialLoader loader = new EssentialLoader("modlauncher");
        final Path stage1File = loader.loadStage1File(gameDir);
        final URL stage1Url = stage1File.toUri().toURL();

        // Create a class loader with which to load stage1
        URLClassLoader classLoader = new URLClassLoader(new URL[]{ stage1Url }, stage0.getClass().getClassLoader());

        // Finally, load stage1
        return (ITransformationService) Class.forName(STAGE1_CLS, true, classLoader)
            .getConstructor(ITransformationService.class)
            .newInstance(stage0);
    }
}
