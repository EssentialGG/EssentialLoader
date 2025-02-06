package gg.essential.loader.stage2.util;

import java.util.function.Supplier;

public class Lazy<T> {
    private Supplier<T> supplier;
    private T value;

    public Lazy(Supplier<T> supplier) {
        this.supplier = supplier;
    }

    public Lazy(T value) {
        this.value = value;
    }

    public T get() {
        if (supplier != null) {
            synchronized (this) {
                value = supplier.get();
                supplier = null;
            }
        }
        return value;
    }
}
