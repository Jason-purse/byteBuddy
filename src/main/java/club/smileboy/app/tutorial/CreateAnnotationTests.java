package club.smileboy.app.tutorial;

import net.bytebuddy.ByteBuddy;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public class CreateAnnotationTests {
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RuntimeDefinition {
        String value() default "";
    }

    public static class RuntimeDefinitionImpl implements RuntimeDefinition {
        @Override
        public Class<? extends Annotation> annotationType() {
            return RuntimeDefinition.class;
        }

        @Override
        public String value() {
            return "";
        }
    }

    @Test
    public void annotationTests() {
        Class<?> loaded = new ByteBuddy()
                .subclass(Object.class)
                .annotateType(new RuntimeDefinitionImpl())
                .make()
                .load(ClassLoader.getSystemClassLoader())
                .getLoaded();
    }

}
