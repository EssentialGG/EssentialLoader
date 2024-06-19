package gg.essential.loader.stage1;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.TypesafeMap;
import net.minecraftforge.fml.loading.FMLLoader;

import java.nio.file.Path;
import java.nio.file.Paths;

@SuppressWarnings("unused")
public class EssentialMixinPluginLoader {
    private static final String KEY_LOADED = "gg.essential.loader.stage1.loaded";

    public EssentialMixinPluginLoader() throws Exception {
        // Check if another transformation service has already loaded stage2 (we do not want to load it twice)
        final TypesafeMap blackboard = Launcher.INSTANCE.blackboard();
        final TypesafeMap.Key<ITransformationService> LOADED =
                TypesafeMap.Key.getOrCreate(blackboard, KEY_LOADED, ITransformationService.class);
        if (blackboard.get(LOADED).isPresent()) {
            return;
        }

        final Path gameDir = Launcher.INSTANCE.environment()
                .getProperty(IEnvironment.Keys.GAMEDIR.get())
                .orElse(Paths.get("."));

        EssentialLoader loader = EssentialLoader.getInstance("modlauncher", "forge_" + FMLLoader.versionInfo().mcVersion());
        loader.load(gameDir);

        loader.getStage2().getClass()
                .getMethod("loadFromMixin", Path.class)
                .invoke(loader.getStage2(), gameDir);
    }
}
