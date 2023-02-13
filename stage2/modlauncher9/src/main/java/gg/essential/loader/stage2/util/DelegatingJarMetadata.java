package gg.essential.loader.stage2.util;

import cpw.mods.jarhandling.JarMetadata;

import java.lang.module.ModuleDescriptor;

public class DelegatingJarMetadata implements JarMetadata {
    protected final JarMetadata delegate;

    public DelegatingJarMetadata(JarMetadata delegate) {
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
        return delegate.descriptor();
    }
}
