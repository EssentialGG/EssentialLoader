package gg.essential.loader.stage2.modlauncher;

import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

class Forge_37_0_0_ModLocator extends AbstractJarFileLocator implements EssentialModLocator {
    private Stream<Path> paths;

    public Iterable<ModFile> scanMods(Stream<Path> paths) {
        this.paths = paths;
        return scanMods().stream().map(it -> (ModFile) it)::iterator;
    }

    @Override
    public Stream<Path> scanCandidates() {
        return this.paths;
    }

    @Override
    public String name() {
        return "essential-loader";
    }

    @Override
    public void initArguments(Map<String, ?> arguments) {
    }
}
