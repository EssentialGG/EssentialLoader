package gg.essential.loader.stage1;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

@SuppressWarnings("unused")
public class EssentialSetupPreLaunch implements PreLaunchEntrypoint {
    private final EssentialLoader loader;

    public EssentialSetupPreLaunch(final PreLaunchEntrypoint stage0) throws Exception {
        final FabricLoader fabricLoader = FabricLoader.getInstance();

        final String mcVersion = fabricLoader.getModContainer("minecraft")
            .map(it -> it.getMetadata().getVersion().getFriendlyString())
            .orElse("unknown");

        this.loader = EssentialLoader.getInstance("fabric_" + mcVersion);
        this.loader.load(fabricLoader.getGameDir());
    }

    @Override
    public void onPreLaunch() {
        this.loader.initialize();
    }
}
