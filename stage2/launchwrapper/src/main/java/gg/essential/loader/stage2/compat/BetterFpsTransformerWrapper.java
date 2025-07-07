package gg.essential.loader.stage2.compat;

import net.minecraft.launchwrapper.IClassTransformer;

// Wraps transformers from BetterFPS which replace a null class with an empty one, breaking lots of stuff.
public class BetterFpsTransformerWrapper implements IClassTransformer {
    private final IClassTransformer delegate;

    public BetterFpsTransformerWrapper(IClassTransformer delegate) {
        this.delegate = delegate;
    }

    @Override
    public byte[] transform(String name, String transformedName, byte[] basicClass) {
        if (basicClass == null) return null;
        return delegate.transform(name, transformedName, basicClass);
    }
}
