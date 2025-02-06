package gg.essential.loader.stage2.modlauncher;

import cpw.mods.jarhandling.JarContents;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.readers.JarModsDotTomlModFileReader;
import net.neoforged.neoforgespi.locating.ModFileDiscoveryAttributes;

import java.nio.file.Path;
import java.util.stream.Stream;

public class NeoForge_4_0_0_ModLocator implements EssentialModLocator_NeoForge {
    @Override
    public Iterable<ModFile> scanMods(Stream<Path> paths) {
        JarModsDotTomlModFileReader modFileReader = new JarModsDotTomlModFileReader();
        return paths.map(it -> (ModFile) modFileReader.read(JarContents.of(it), ModFileDiscoveryAttributes.DEFAULT))::iterator;
    }
}
