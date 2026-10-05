package com.slabbed.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import net.minecraft.world.level.block.Rotation;

/**
 * The suite's test annotation on this line. Same attributes and defaults as the Fabric game-test
 * annotation the suite was written against, so a test method reads identically on every line; the
 * registrar ({@link SlabbedGameTests}) turns each annotated method into a vanilla function test.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {
    String environment() default "minecraft:default";

    String structure() default "slabbed_gametest:empty";

    int maxTicks() default 20;

    int setupTicks() default 0;

    boolean required() default true;

    Rotation rotation() default Rotation.NONE;

    boolean manualOnly() default false;

    int maxAttempts() default 1;

    int requiredSuccesses() default 1;

    boolean skyAccess() default false;
}
