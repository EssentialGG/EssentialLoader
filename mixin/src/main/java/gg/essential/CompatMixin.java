package gg.essential;

import org.spongepowered.asm.mixin.Mixin;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

/**
 * Like {@link Mixin}, but at build time, and no injectors. But it allows public static methods!
 */
@Target(ElementType.TYPE)
public @interface CompatMixin {
    Class<?> value() default Void.class;

    String target() default "";
}
