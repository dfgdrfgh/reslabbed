package com.slabbed.gametest;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInstance;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import org.slf4j.Logger;

/**
 * Turns every {@link GameTest}-annotated method of the registered classes into a game test. Names
 * follow the Fabric runner's shape ({@code <modid>:<classname>/<methodname>}, lower case) so the
 * suite's own log greps and the count gate keep working across lines. Each test is its own
 * {@link GameTestInstance} that invokes the method; environments are registered in code (one per
 * distinct name the suite uses) so every test holds a bound environment at registration time.
 */
public final class SlabbedGameTests {
    public static final String MOD_ID = "slabbed_gametest";
    private static final Logger LOGGER = LogUtils.getLogger();

    private record TestMethod(Class<?> owner, Method method, GameTest annotation, Identifier id) {
    }

    private static final List<TestMethod> METHODS = discover();

    private SlabbedGameTests() {
    }

    /** Forces discovery (and so every test class's static initialisation) to run now. */
    public static int prepare() {
        return METHODS.size();
    }

    private static List<TestMethod> discover() {
        List<TestMethod> found = new ArrayList<>();
        List<String> classNames = SlabbedGameTestClasses.classes();
        for (String className : classNames) {
            Class<?> owner;
            try {
                owner = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new IllegalStateException("registered test class is missing: " + className, e);
            }
            for (Method method : owner.getDeclaredMethods()) {
                GameTest annotation = method.getAnnotation(GameTest.class);
                if (annotation == null) {
                    continue;
                }
                if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1
                        || !GameTestHelper.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    throw new IllegalStateException("test method must be an instance method taking a "
                            + "GameTestHelper: " + owner.getName() + "#" + method.getName());
                }
                String path = owner.getSimpleName().toLowerCase(Locale.ROOT) + "/"
                        + method.getName().toLowerCase(Locale.ROOT);
                found.add(new TestMethod(owner, method, annotation,
                        Identifier.fromNamespaceAndPath(MOD_ID, path)));
            }
        }
        LOGGER.info("[slabbed_gametest] discovered {} test methods in {} classes",
                found.size(), classNames.size());
        return found;
    }

    /**
     * The full test-data constructor: 26.2 takes (environment, structure, maxTicks, setupTicks, required,
     * rotation, manualOnly, maxAttempts, requiredSuccesses, skyAccess, padding); 26.3 inserts the level
     * dimension after the environment. One source builds against either, so the constructor is chosen
     * by its parameter count and the overworld is supplied where a dimension is asked for.
     */
    @SuppressWarnings("unchecked")
    private static TestData<Holder<TestEnvironmentDefinition<?>>> testData(
            Holder<TestEnvironmentDefinition<?>> environment, GameTest a) {
        for (java.lang.reflect.Constructor<?> constructor : TestData.class.getConstructors()) {
            int n = constructor.getParameterCount();
            if (n != 11 && n != 12) {
                continue;
            }
            List<Object> args = new ArrayList<>(List.of(environment));
            if (n == 12) {
                args.add(net.minecraft.world.level.Level.OVERWORLD);
            }
            args.addAll(List.of(Identifier.parse(a.structure()), a.maxTicks(), a.setupTicks(), a.required(),
                    a.rotation(), a.manualOnly(), a.maxAttempts(), a.requiredSuccesses(), a.skyAccess(), 0));
            try {
                return (TestData<Holder<TestEnvironmentDefinition<?>>>) constructor.newInstance(args.toArray());
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("TestData constructor failed", e);
            }
        }
        throw new IllegalStateException("no TestData constructor with 11 or 12 parameters on this game version");
    }

    /** Registers one environment per distinct name and one test instance per method. */
    public static void registerTests(RegisterGameTestsEvent event) {
        Map<String, Holder<TestEnvironmentDefinition<?>>> environments = new HashMap<>();
        for (TestMethod test : METHODS) {
            GameTest a = test.annotation();
            Holder<TestEnvironmentDefinition<?>> environment = environments.computeIfAbsent(a.environment(),
                    name -> event.registerEnvironment(environmentId(name), new TestEnvironmentDefinition.AllOf(List.of())));
            event.registerTest(test.id(), new MethodTestInstance(test, testData(environment, a)));
        }
        LOGGER.info("[slabbed_gametest] registered {} tests in {} environments", METHODS.size(), environments.size());
    }

    /**
     * The suite's environment names map onto this mod's own registrations: the vanilla default
     * becomes {@code slabbed_gametest:default} (an empty definition, as vanilla's is) and a named
     * one keeps its path. Every distinct environment is its own batch, which is what the suite's
     * one serial batch needs.
     */
    private static Identifier environmentId(String name) {
        Identifier parsed = Identifier.parse(name);
        return Identifier.fromNamespaceAndPath(MOD_ID, parsed.getPath());
    }

    private static final class MethodTestInstance extends GameTestInstance {
        private final TestMethod test;

        MethodTestInstance(TestMethod test, TestData<Holder<TestEnvironmentDefinition<?>>> data) {
            super(data);
            this.test = test;
        }

        @Override
        public void run(GameTestHelper helper) {
            invoke(test, helper);
        }

        @Override
        public MapCodec<? extends GameTestInstance> codec() {
            return MapCodec.unit(this);
        }

        @Override
        protected MutableComponent typeDescription() {
            return Component.literal("Slabbed test method");
        }

        @Override
        public Component describe() {
            return Component.literal(test.id().toString());
        }
    }

    private static void invoke(TestMethod test, GameTestHelper helper) {
        Object instance;
        try {
            var constructor = test.owner().getDeclaredConstructor();
            constructor.setAccessible(true);
            instance = constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot instantiate " + test.owner().getName(), e);
        }
        try {
            test.method().setAccessible(true);
            test.method().invoke(instance, helper);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            if (cause instanceof Error error) {
                throw error;
            }
            throw new RuntimeException(cause);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }
}
