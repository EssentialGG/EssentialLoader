package gg.essential.loader.stage2;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gg.essential.loader.stage2.data.ModJarMetadata;
import net.minecraft.launchwrapper.Launch;
import net.minecraftforge.common.ForgeVersion;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Supplier;

public class EssentialModUpdater implements Supplier<String> {
    @Override
    public String get() {
        // EssentialLoaderBase expects to be called only once per boot for its fallback gui to behave properly
        // and running it multiple times is wasteful anyway.
        // Using a system property, so we don't run multiple times even if invoked via multiple class loaders.
        String key = "gg.essential.loader.stage2.mod-downloader-result";
        String result = System.getProperty(key);
        if (result == null) {
            result = load();
            System.setProperty(key, result);
        }
        return result;
    }

    private String load() {
        JsonArray result = new JsonArray();

        EssentialLoaderBase loader = new EssentialLoaderBase(getGameDir(), getPlatform()) {
            @Override
            protected void addToClasspath(Mod mod, ModJarMetadata jarMeta, Path mainJar, List<Path> innerJars) {
                JsonObject modJson = new JsonObject();
                modJson.addProperty("id", mod.id.getModSlug());
                modJson.addProperty("version", jarMeta.getVersion().getVersion());
                modJson.addProperty("file", mainJar.toAbsolutePath().toString());
                result.add(modJson);

                // Ignoring innerJars because we've never used that mechanism for our launchwrapper versions and won't
                // be using it in the future either since it doesn't provide ids nor versions of the inner jars.
            }

            @Override
            protected void addToClasspath(Path path) { throw new UnsupportedOperationException(); }
            @Override
            protected void loadPlatform() {}
            @Override
            protected ClassLoader getModClassLoader() { return null; }
            @Override
            protected String getRequiredStage2VersionIfOutdated(Path modFile) {
                return null; // we support in-place loader upgrades, we never need to prompt the user for a restart
            }
        };
        try {
            loader.load();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return new Gson().toJson(result);
    }

    static Path getGameDir() {
        File minecraftHome = Launch.minecraftHome;
        if (minecraftHome == null) minecraftHome = new File(".");
        return minecraftHome.toPath();
    }

    static String getPlatform() {
        try {
            // Accessing via reflection so the compiler does not inline the value at build time.
            return "forge_" + ForgeVersion.class.getDeclaredField("mcVersion").get(null);
        } catch (IllegalAccessException | NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }
}
