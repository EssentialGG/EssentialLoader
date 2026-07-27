package gg.essential.loader.stage2.util;

import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class AugmentedJarOrPathList extends DelegatingList<Object> {
    private final Consumer<List<Object>> augmentation;

    public AugmentedJarOrPathList(List<Object> inner, Consumer<List<Object>> augmentation) {
        super(inner);
        this.augmentation = augmentation;
    }

    // Called by ModLauncher when finalizing the layer
    @Override
    public @NotNull Stream<Object> stream() {
        augmentation.accept(this);
        return super.stream();
    }

    // MinecraftForge's ModLauncher fork uses this instead:
    // https://github.com/MinecraftForge/ModLauncher/commit/1c5695789fd26bef41328900bb82898c14760629#diff-19fd94ffbddfd7c1d3967bc8152bf96d12ce47c23e7f9e83f734198175c428e1
    @Override
    public <T1> T1 @NotNull [] toArray(T1 @NotNull [] a) {
        augmentation.accept(this);
        return super.toArray(a);
    }
}
