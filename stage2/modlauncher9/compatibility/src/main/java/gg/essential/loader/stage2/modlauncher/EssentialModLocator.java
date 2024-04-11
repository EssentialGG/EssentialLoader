package gg.essential.loader.stage2.modlauncher;

import net.minecraftforge.fml.loading.moddiscovery.ModFile;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

public interface EssentialModLocator extends IModLocator {
    Iterable<ModFile> scanMods(Stream<Path> paths);
}
