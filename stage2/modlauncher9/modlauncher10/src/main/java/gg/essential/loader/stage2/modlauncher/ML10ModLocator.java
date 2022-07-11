package gg.essential.loader.stage2.modlauncher;

import net.minecraftforge.fml.loading.moddiscovery.AbstractJarFileModLocator;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;

import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Stream;

class ML10ModLocator extends AbstractJarFileModLocator implements EssentialModLocator {
    private Stream<Path> paths;

    public Iterable<ModFile> scanMods(Stream<Path> paths) {
        this.paths = paths;
        return scanMods().stream().map(it -> (ModFile) it.file())::iterator;
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
