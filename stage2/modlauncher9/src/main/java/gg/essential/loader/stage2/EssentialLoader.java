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
            .flatMap(it -> {
                try {
                    return it.getLayer(IModuleLayerManager.Layer.GAME);
                } catch (NullPointerException e) {
                    // Workaround ModLoader being stupid and throwing a NPE instead of just returning Optional.empty()
                    // as its return type would suggest.
                    return Optional.empty();
                }
            })
            .flatMap(it -> it.findModule("essential"))
            .flatMap(it -> Optional.ofNullable(it.getClassLoader()))
            .orElse(null);
    }

    @Override
    protected void addToClasspath(final Path path) {
        this.transformationService.addToClasspath(path);
    }
}
