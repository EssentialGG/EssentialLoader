package gg.essential.loader.stage0;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;

import static gg.essential.loader.stage0.EssentialLoader.STAGE1_PKG;

public class EssentialSetupPreLaunch implements PreLaunchEntrypoint {
    private static final String STAGE1_CLS = STAGE1_PKG + "EssentialSetupPreLaunch";

    private final EssentialLoader loader = new EssentialLoader("fabric");
    private final PreLaunchEntrypoint stage1;

    public EssentialSetupPreLaunch() {
        try {
            this.stage1 = loadStage1(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private PreLaunchEntrypoint loadStage1(PreLaunchEntrypoint stage0) throws Exception {
        // Extract/update stage1 from embedded jars
        final Path stage1File = this.loader.loadStage1File(FabricLoader.getInstance().getGameDir());
        final URL stage1Url = stage1File.toUri().toURL();

        // Create a class loader with which to load stage1
        URLClassLoader classLoader = new URLClassLoader(new URL[]{ stage1Url }, getClass().getClassLoader());

        // Finally, load stage1
        return (PreLaunchEntrypoint) Class.forName(STAGE1_CLS, true, classLoader)
            .getConstructor(PreLaunchEntrypoint.class)
            .newInstance(stage0);
    }

    @Override
    public void onPreLaunch() {
        this.stage1.onPreLaunch();
    }
}
