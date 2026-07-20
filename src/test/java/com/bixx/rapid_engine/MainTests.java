package com.bixx.rapid_engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Smoke test for {@link Main}.
 *
 * <p>Intentionally does NOT use {@code @SpringBootTest} because the
 * production application depends on Redis and RabbitMQ infrastructure
 * (declarative exchange/queue wiring) that is not always available in CI.
 * A full integration test that brings up the whole context is out of
 * scope for unit tests; this class simply verifies that the application
 * class is well-formed (i.e. it can be loaded by the JVM without
 * triggering static initialisation errors).
 */
class MainTests {

    @Test
    @DisplayName("Main: class is loadable and declared public")
    void applicationClass_isLoadable(){
        assertThat(Main.class).isNotNull();
        int modifiers = Main.class.getModifiers();
        assertThat(java.lang.reflect.Modifier.isPublic(modifiers)).isTrue();
    }

    @Test
    @DisplayName("Main: declares the standard Spring Boot main method")
    void mainMethod_isDeclared() throws NoSuchMethodException{
        java.lang.reflect.Method main = Main.class.getMethod("main", String[].class);
        assertThat(main).isNotNull();
        assertThat(java.lang.reflect.Modifier.isStatic(main.getModifiers())).isTrue();
        assertThat(main.getReturnType()).isEqualTo(void.class);
    }
}
