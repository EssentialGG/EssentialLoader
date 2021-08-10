package net.minecraft.client.main;

import net.minecraft.client.MinecraftClient;

import java.io.File;

public class Main {
    public static void main(String[] args) {
        File gameDir = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--gameDir")) {
                gameDir = new File(args[i + 1]);
            }
        }
        new MinecraftClient(gameDir); // need this for fabric-loader to patch in its entry point
    }
}
