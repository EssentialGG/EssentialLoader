package gg.essential.loader.stage2.util;

import org.jetbrains.annotations.NotNull;

import java.util.AbstractList;
import java.util.List;
import java.util.stream.Stream;

public class DelegatingList<T> extends AbstractList<T> {
    private final List<T> delegate;

    public DelegatingList(List<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public T get(int index) {
        return delegate.get(index);
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public T set(int index, T element) {
        return delegate.set(index, element);
    }

    @Override
    public void add(int index, T element) {
        delegate.add(index, element);
    }

    @Override
    public T remove(int index) {
        return delegate.remove(index);
    }

    //
    // These methods are called by ModLauncher, so we'll forward them directly in case
    // another mod also replaces the list and relies on them.
    //

    @Override
    public boolean add(T t) {
        return delegate.add(t);
    }

    @Override
    public @NotNull Stream<T> stream() {
        return delegate.stream();
    }

    // Used by MinecraftForge's ModLauncher fork:
    // https://github.com/MinecraftForge/ModLauncher/commit/1c5695789fd26bef41328900bb82898c14760629#diff-19fd94ffbddfd7c1d3967bc8152bf96d12ce47c23e7f9e83f734198175c428e1
    @Override
    public <T1> T1 @NotNull [] toArray(T1 @NotNull [] a) {
        return delegate.toArray(a);
    }
}
