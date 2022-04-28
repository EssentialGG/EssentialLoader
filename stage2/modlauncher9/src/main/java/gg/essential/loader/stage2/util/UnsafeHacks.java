package gg.essential.loader.stage2.util;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
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

    @SuppressWarnings("unchecked")
    public static <T, U> Function<T, U> makeGetter(Field field) {
        long offset = unsafe.objectFieldOffset(field);
        return object -> (U) unsafe.getObject(object, offset);
    }

}
