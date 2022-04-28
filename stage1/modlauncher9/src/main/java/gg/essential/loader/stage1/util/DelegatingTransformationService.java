package gg.essential.loader.stage1.util;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.IModuleLayerManager;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import joptsimple.OptionSpecBuilder;
import org.jetbrains.annotations.NotNull;

import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public class DelegatingTransformationService implements ITransformationService {

    @NotNull
    protected ITransformationService delegate;

    public DelegatingTransformationService(@NotNull ITransformationService delegate) {
        this.delegate = delegate;
    }

    @Override
    public @NotNull String name() {
        return this.delegate.name();
    }

    @Override
    public void arguments(BiFunction<String, String, OptionSpecBuilder> argumentBuilder) {
        this.delegate.arguments(argumentBuilder);
    }

    @Override
    public void argumentValues(OptionResult option) {
        this.delegate.argumentValues(option);
    }

    @Override
    public void initialize(IEnvironment environment) {
        this.delegate.initialize(environment);
    }

    @Override
    public List<Resource> beginScanning(IEnvironment environment) {
        return this.delegate.beginScanning(environment);
    }

    @Override
    public List<Resource> completeScan(IModuleLayerManager layerManager) {
        return this.delegate.completeScan(layerManager);
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {
        this.delegate.onLoad(env, otherServices);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public @NotNull List<ITransformer> transformers() {
        return this.delegate.transformers();
    }

    @Override
    public Map.Entry<Set<String>, Supplier<Function<String, Optional<URL>>>> additionalClassesLocator() {
        return this.delegate.additionalClassesLocator();
    }

    @Override
    public Map.Entry<Set<String>, Supplier<Function<String, Optional<URL>>>> additionalResourcesLocator() {
        return this.delegate.additionalResourcesLocator();
    }
}
