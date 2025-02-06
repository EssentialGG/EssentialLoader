package gg.essential.loader.stage2.modlauncher;

import cpw.mods.jarhandling.SecureJar;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.fml.loading.moddiscovery.ModFile;
import net.neoforged.fml.loading.moddiscovery.ModValidator;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public interface EssentialModLocator_NeoForge extends EssentialModLocator {
    Iterable<ModFile> scanMods(Stream<Path> paths);

    @Override
    default boolean injectMods(List<SecureJar> modJars) throws ReflectiveOperationException {
        Field modValidatorField = FMLLoader.class.getDeclaredField("modValidator");
        modValidatorField.setAccessible(true);
        ModValidator modValidator = (ModValidator) modValidatorField.get(null);

        if (modValidator == null) {
            return false;
        }

        Field candidateModsField = ModValidator.class.getDeclaredField("candidateMods");
        candidateModsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<ModFile> modFiles = (List<ModFile>) candidateModsField.get(modValidator);

        for (ModFile modFile : this.scanMods(modJars.stream().map(SecureJar::getPrimaryPath))) {
            modFile.identifyMods();
            modFiles.add(modFile);
        }

        return true;
    }
}
