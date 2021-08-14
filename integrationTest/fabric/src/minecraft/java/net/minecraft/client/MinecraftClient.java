package net.minecraft.client;

import java.io.File;

public class MinecraftClient {
    @SuppressWarnings({"FieldCanBeLocal", "unused"}) // need this for fabric-loader to patch in its entry point
    private final File runDirectory;

    public MinecraftClient(File gameDir) {
        runDirectory = gameDir;

        // Simulate regular exit (MC does this as well, instead of just returning from main)
        System.exit(0);
    }
}
