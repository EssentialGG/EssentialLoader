package gg.essential.loader.stage2.modlauncher;

import net.minecraftforge.fml.loading.moddiscovery.AbstractModProvider;
import net.minecraftforge.fml.loading.moddiscovery.ModFile;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class Forge_49_0_38_ModLocator extends AbstractModProvider implements EssentialModLocator_Forge {
    private Stream<Path> paths;

    @Override
    public Iterable<ModFile> scanMods(Stream<Path> paths) {
        this.paths = paths;
        return scanMods().stream().map(it -> (ModFile) it.file())::iterator;
    }

    @Override
    public List<ModFileOrException> scanMods() {
        return paths.map(this::createMod).toList();
    }

    @Override
    public String name() {
        return "essential-loader";
    }
}
