package gg.essential.loader.stage2.util;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.function.Function;

public class UnsafeHacks {

    private static final Unsafe unsafe;
    static {
        try {
            Field field = Unsafe.class.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            unsafe = (Unsafe) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T extends U, U> T allocateCopy(U source, Class<T> copyClass) {
        Class<?> sourceClass = source.getClass();
        if (!sourceClass.isAssignableFrom(copyClass)) {
            throw new IllegalArgumentException(copyClass + " does not extend " + copyClass);
        }

        Object copy;
        try {
            copy = unsafe.allocateInstance(copyClass);
        } catch (InstantiationException e) {
            throw new RuntimeException(e);
        }
        copyRecursive(sourceClass, source, copy);
        return copyClass.cast(copy);
    }

    private static void copyRecursive(Class<?> cls, Object src, Object dst) {
        if (cls == null || cls == Object.class) {
            return;
        }

        for (Field field : cls.getDeclaredFields()) {
            Accessor<Object, Object> accessor = makeAccessor(field);
            accessor.set(dst, accessor.get(src));
        }
    }

    public static <T, U> Function<T, U> makeGetter(Class<? super T> cls, String field) {
        return UnsafeHacks.<T, U>makeAccessor(cls, field)::get;
    }

    public static <T, U> Function<T, U> makeGetter(Field field) {
        return UnsafeHacks.<T, U>makeAccessor(field)::get;
    }

    public static <O, T> Accessor<O, T> makeAccessor(Class<? super O> cls, String field) {
        try {
            return makeAccessor(cls.getDeclaredField(field));
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    public static <O, T> Accessor<O, T> makeAccessor(Field field) {
        if (field.getType().isPrimitive()) {
            throw new UnsupportedOperationException("Only Object types are supported.");
        }
        if ((field.getModifiers() & Modifier.STATIC) != 0) {
            Object base = unsafe.staticFieldBase(field);
            long offset = unsafe.staticFieldOffset(field);
            return new Accessor<O, T>() {
                @SuppressWarnings("unchecked")
                @Override
                public T get(O owner) {
                    return (T) unsafe.getObject(base, offset);
                }

                @Override
                public void set(O owner, T value) {
                    unsafe.putObject(base, offset, value);
                }
            };
        } else {
            long offset = unsafe.objectFieldOffset(field);
            return new Accessor<O, T>() {
                @SuppressWarnings("unchecked")
                @Override
                public T get(O owner) {
                    return (T) unsafe.getObject(owner, offset);
                }

                @Override
                public void set(O owner, T value) {
                    unsafe.putObject(owner, offset, value);
                }
            };
        }
    }

    public interface Accessor<O, T> {
        T get(O owner);
        void set(O owner, T value);

        default void update(O owner, Function<T, T> func) {
            set(owner, func.apply(get(owner)));
        }
    }
}
