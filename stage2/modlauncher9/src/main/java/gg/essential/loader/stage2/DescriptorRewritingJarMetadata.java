package gg.essential.loader.stage2;

import cpw.mods.jarhandling.JarMetadata;
import cpw.mods.jarhandling.SecureJar;

import java.lang.module.ModuleDescriptor;

/**
 * Re-creates the {@link #descriptor()} with an updated {@link SecureJar#getPackages() package list}.
 * This is needed when one adds classes (even internal ones) to a {@link SecureJar} in previously non-existent packages
 * because the module system needs to know about all packages because ModLauncher will use that package list to build
 * a lookup table.
 */
public class DescriptorRewritingJarMetadata implements JarMetadata {
    private final SecureJar secureJar;
    private final JarMetadata delegate;
    private ModuleDescriptor descriptor;

    public DescriptorRewritingJarMetadata(SecureJar secureJar, JarMetadata delegate) {
        this.secureJar = secureJar;
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return delegate.name();
    }

    @Override
    public String version() {
        return delegate.version();
    }

    @Override
    public ModuleDescriptor descriptor() {
        if (this.descriptor == null) {
            ModuleDescriptor org = delegate.descriptor();
            ModuleDescriptor.Builder builder = ModuleDescriptor.newModule(org.name(), org.modifiers());
            builder.packages(secureJar.getPackages());
            if (!org.isAutomatic()) {
                org.requires().forEach(builder::requires);
                org.exports().forEach(builder::exports);
                if (!org.isOpen()) {
                    org.opens().forEach(builder::opens);
                }
                org.uses().forEach(builder::uses);
            }
            org.provides().forEach(builder::provides);
            org.mainClass().ifPresent(builder::mainClass);
            this.descriptor = builder.build();
        }
        return this.descriptor;
    }
}
