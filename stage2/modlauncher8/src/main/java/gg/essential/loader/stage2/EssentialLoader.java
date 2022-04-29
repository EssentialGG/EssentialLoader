package gg.essential.loader.stage2;

import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformingClassLoader;

import java.nio.file.Path;

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
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader instanceof ITransformingClassLoader) {
            return contextClassLoader;
        } else {
            return null;
        }
    }

    @Override
    protected void addToClasspath(final Path path) {
        this.transformationService.addToClasspath(path);
    }
}
