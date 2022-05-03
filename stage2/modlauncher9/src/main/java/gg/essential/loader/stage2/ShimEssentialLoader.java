package gg.essential.loader.stage2;

import cpw.mods.modlauncher.api.ITransformationService;

import java.nio.file.Path;

/**
 * The initial entrypoint for stage2. With ModLauncher, we cannot yet know the MC version at this point, so this is a
 * dummy class, not the actual loader, which gets initialized later by the transformationService once the MC version is
 * available.
 */
public class ShimEssentialLoader {
    private final EssentialTransformationService transformationService;

    @SuppressWarnings("unused") // called via reflection from stage1
    public ShimEssentialLoader(Path gameDir, String fakeGameVersion) {
        this.transformationService = new EssentialTransformationService(gameDir);
    }

    @SuppressWarnings("unused") // called via reflection from stage1
    public ITransformationService getTransformationService() {
        return this.transformationService;
    }

    @SuppressWarnings("unused") // called via reflection from stage1
    public void load() {
        // delayed until ModLauncher exposes the MC version
    }
}
