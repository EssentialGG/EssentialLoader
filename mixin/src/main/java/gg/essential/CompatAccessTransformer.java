package gg.essential;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
public @interface CompatAccessTransformer {
    /**
     * Access flags to be added to the target
     */
    int[] add() default {};

    /**
     * Access flags to be removed from the target
     */
    int[] remove() default {};
}
