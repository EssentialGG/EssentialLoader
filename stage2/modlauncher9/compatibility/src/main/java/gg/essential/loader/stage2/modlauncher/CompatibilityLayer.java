package gg.essential.loader.stage2.modlauncher;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import gg.essential.loader.stage2.util.Lazy;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.jar.Manifest;

/**
 * Provides abstractions for things which changed after ModLauncher 9.
 */
public interface CompatibilityLayer {
    default SecureJar newSecureJarWithCustomMetadata(BiFunction<Lazy<SecureJar>, JarMetadata, JarMetadata> metadataWrapper, Path path) {
        return SecureJar.from(jar -> metadataWrapper.apply(new Lazy<>(jar), JarMetadata.from(jar, path)), path);
    }

    Manifest getManifest(SecureJar jar);

    default Set<String> getPackages(SecureJar secureJar) {
        return secureJar.getPackages();
    }

    default JarMetadata getJarMetadata(SecureJar secureJar, Path path) {
        return JarMetadata.from(secureJar, path);
    }
}
