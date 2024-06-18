package gg.essential.loader.stage2;

import cpw.mods.modlauncher.api.ITransformationService;
import gg.essential.loader.stage2.jvm.ForkedJvmLoaderSwingUI;
import net.minecraftforge.fml.loading.FMLLoader;

import java.io.IOException;
import java.nio.file.Path;

/**
 * The initial entrypoint for stage2. With ModLauncher, we cannot yet know the MC version at this point, so this is a
 * dummy class, not the actual loader, which gets initialized later by the transformationService once the MC version is
 * available.
 */
public class EssentialLoader {
    private final EssentialTransformationService transformationService;

    @SuppressWarnings("unused") // called via reflection from stage1
    public EssentialLoader(Path gameDir, String fakeGameVersion) {
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

    @SuppressWarnings("unused") // called via reflection from stage1
    public void loadFromMixin(Path gameDir) throws IOException {
        final Path modsDir = gameDir.resolve("mods");
        LoaderUI ui = LoaderUI.all(
                new LoaderLoggingUI().updatesEveryMillis(1000),
                new ForkedJvmLoaderSwingUI().updatesEveryMillis(1000 / 60)
        );
        ui.start();
        DedicatedJarLoader.downloadDedicatedJar(ui, modsDir, "forge_" + FMLLoader.versionInfo().mcVersion());
        ui.complete();
        RestartUI restartUI = new RestartUI("Restart Required!", "One of the mods you have installed requires Essential. To complete the installation process, please restart.");
        restartUI.show();
        restartUI.waitForClose();
        System.exit(0);
    }
}
