package gg.essential.loader.stage0;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import gg.essential.loader.stage0.util.DelegatingTransformationServiceBase;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.nio.file.Paths;

import static gg.essential.loader.stage0.EssentialLoader.STAGE1_PKG;

abstract class EssentialTransformationServiceBase extends DelegatingTransformationServiceBase {

    private static final String STAGE1_CLS = STAGE1_PKG + "EssentialTransformationService";

    public EssentialTransformationServiceBase(String variant) {
        super(stage0 -> loadStage1OrThrow(stage0, variant));
    }

    private static ITransformationService loadStage1OrThrow(ITransformationService stage0, String variant) {
        try {
            return loadStage1(stage0, variant);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static ITransformationService loadStage1(ITransformationService stage0, String variant) throws Exception {
        Path gameDir = Launcher.INSTANCE.environment()
            .getProperty(IEnvironment.Keys.GAMEDIR.get())
            .orElseGet(() -> Paths.get("."));

        // Extract/update stage1 from embedded jars
        final EssentialLoader loader = new EssentialLoader(variant);
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
