package gg.essential.loader.stage1.util;

import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class FallbackTransformationService implements ITransformationService {

    private final String name;

    public FallbackTransformationService(final String name) {
        this.name = name;
    }

    @Override
    public @NotNull String name() {
        return this.name;
    }

    @Override
    public void initialize(IEnvironment environment) {
    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) {
    }

    @SuppressWarnings("rawtypes")
    @Override
    public @NotNull List<ITransformer> transformers() {
        return Collections.emptyList();
    }
}
