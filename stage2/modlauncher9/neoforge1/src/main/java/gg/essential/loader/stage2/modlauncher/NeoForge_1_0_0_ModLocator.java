package gg.essential.loader.stage2.modlauncher;

import net.neoforged.fml.loading.moddiscovery.AbstractJarFileModProvider;
import net.neoforged.fml.loading.moddiscovery.ModFile;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

public class NeoForge_1_0_0_ModLocator extends AbstractJarFileModProvider implements EssentialModLocator_NeoForge {
    @Override
    public void initArguments(Map<String, ?> map) {
    }

    @Override
    public Iterable<ModFile> scanMods(Stream<Path> paths) {
        return paths.map(this::createMod).map(it -> (ModFile) it.file())::iterator;
    }

    @Override
    public String name() {
        return "essential-loader";
    }
}
