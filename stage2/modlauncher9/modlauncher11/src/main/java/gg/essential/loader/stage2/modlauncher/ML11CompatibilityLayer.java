package gg.essential.loader.stage2.modlauncher;

import cpw.mods.jarhandling.JarContents;
import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;
import gg.essential.loader.stage2.util.Lazy;

import java.nio.file.Path;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.jar.Manifest;

public class ML11CompatibilityLayer implements CompatibilityLayer {
    @Override
    public SecureJar newSecureJarWithCustomMetadata(BiFunction<Lazy<SecureJar>, JarMetadata, JarMetadata> metadataWrapper, Path path) {
        SecureJar[] jarHolder = new SecureJar[1];
        JarContents contents = JarContents.of(path);
        JarMetadata metadata = JarMetadata.from(contents);
        metadata = metadataWrapper.apply(new Lazy<>(() -> jarHolder[0]), metadata);
        SecureJar jar = SecureJar.from(contents, metadata);
        jarHolder[0] = jar;
        return jar;
    }

    @Override
    public Manifest getManifest(SecureJar jar) {
        return jar.moduleDataProvider().getManifest();
    }

    @Override
    public Set<String> getPackages(SecureJar secureJar) {
        return secureJar.moduleDataProvider().descriptor().packages();
    }

    @Override
    public JarMetadata getJarMetadata(SecureJar secureJar, Path path) {
        return JarMetadata.from(JarContents.of(path));
    }
}
