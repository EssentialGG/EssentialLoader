package gg.essential.loader.stage1.util;

import cpw.mods.modlauncher.api.IEnvironment;
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

public interface IDelegatingTransformationServiceBase extends ITransformationService {

    ITransformationService delegate();

    @Override
    default @NotNull String name() {
        return this.delegate().name();
    }

    @Override
    default void arguments(BiFunction<String, String, OptionSpecBuilder> argumentBuilder) {
        this.delegate().arguments(argumentBuilder);
    }

    @Override
    default void argumentValues(OptionResult option) {
        this.delegate().argumentValues(option);
    }

    @Override
    default void initialize(IEnvironment environment) {
        this.delegate().initialize(environment);
    }

    @Override
    default void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {
        this.delegate().onLoad(env, otherServices);
    }

    @SuppressWarnings("rawtypes")
    @Override
    default @NotNull List<ITransformer> transformers() {
        return this.delegate().transformers();
    }

    @Override
    default Map.Entry<Set<String>, Supplier<Function<String, Optional<URL>>>> additionalClassesLocator() {
        return this.delegate().additionalClassesLocator();
    }

    @Override
    default Map.Entry<Set<String>, Supplier<Function<String, Optional<URL>>>> additionalResourcesLocator() {
        return this.delegate().additionalResourcesLocator();
    }
}
