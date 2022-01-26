package com.example.mod.tweaker;

import net.minecraft.launchwrapper.Launch;
import org.objectweb.asm.Opcodes;
import sun.com.example.mod.LoadState;
import gg.essential.loader.stage0.EssentialSetupTweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.util.Objects;

public class ExampleModTweaker extends EssentialSetupTweaker {
    static {
        if (Boolean.parseBoolean(System.getProperty("examplemod.exclude_kotlin_from_transformers", "false"))) {
            Launch.classLoader.addTransformerExclusion("kotlin.something.");
        }
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {
        super.injectIntoClassLoader(classLoader);

        if (Boolean.parseBoolean(System.getProperty("examplemod.require_asm52", "false"))) {
            // Check that the package implementation version is correct
            // (currently it should be null because we do not JiJ the asm lib, in the future it should be "5.2")
            String version = Opcodes.class.getPackage().getImplementationVersion();
            if (!Objects.equals(version, null)) {
                throw new RuntimeException("Unexpected ASM version: " + version);
            }

            // Check that a 5.1+ class is present
            try {
                Class.forName("org.objectweb.asm.commons.ClassRemapper");
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        LoadState.checkForRelaunch();
        LoadState.tweaker = true;
    }
}
