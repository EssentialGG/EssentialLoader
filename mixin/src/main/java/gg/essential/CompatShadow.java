package gg.essential;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.METHOD, ElementType.FIELD})
public @interface CompatShadow {
    /**
     * If set, specifies the name of the original method and renames it to the name of the shadow method.
     * This allows you to overwrite the original method but still call its content.
     */
    String original() default "";
}
