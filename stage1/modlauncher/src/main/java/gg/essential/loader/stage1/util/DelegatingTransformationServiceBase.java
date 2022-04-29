package gg.essential.loader.stage1.util;

import cpw.mods.modlauncher.api.ITransformationService;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public abstract class DelegatingTransformationServiceBase implements IDelegatingTransformationServiceBase {
    @NotNull
    protected ITransformationService delegate;

    public DelegatingTransformationServiceBase(Function<ITransformationService, ITransformationService> delegateProvider) {
        this.delegate = delegateProvider.apply(this);
    }

    @Override
    public ITransformationService delegate() {
        return delegate;
    }
}
