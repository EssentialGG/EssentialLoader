package gg.essential.loader.stage2;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;

import java.nio.file.Path;
import java.util.Optional;

public class EssentialLoader extends EssentialLoaderBase {
    private final EssentialTransformationService transformationService = new EssentialTransformationService();

    public EssentialLoader(Path gameDir, String gameVersion) {
        super(gameDir, gameVersion, true);
    }

    @SuppressWarnings("unused") // called via reflection from stage1
    public ITransformationService getTransformationService() {
        return this.transformationService;
    }

    @Override
    protected void loadPlatform() {
    }

    @Override
    protected boolean classpathUpdatesImmediately() {
        return false;
    }

    @Override
    protected ClassLoader getModClassLoader() {
        return Launcher.INSTANCE.findLayerManager()
            .flatMap(it -> it.getLayer(IModuleLayerManager.Layer.GAME))
            .flatMap(it -> it.findModule("essential"))
            .flatMap(it -> Optional.ofNullable(it.getClassLoader()))
            .orElse(null);
    }

    @Override
    protected void addToClasspath(final Path path) {
        this.transformationService.addToClasspath(path);
    }
}
