package club.smileboy.app.method.define;

import club.smileboy.app.method.proxy.Bar;
import club.smileboy.app.method.proxy.Foo;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.description.modifier.ModifierContributor;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import net.minidev.asm.ex.NoSuchFieldException;
import org.junit.jupiter.api.Test;
import org.mockito.creation.instance.InstantiationException;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import static net.bytebuddy.matcher.ElementMatchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author FLJ
 * @date 2022/8/9
 * @time 17:18
 * @Description 方法定义 ... 和方法新增
 */
public class MethodDefineTests {

    @Test
    public void fieldOverride() throws NoSuchFieldException, InstantiationException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, java.lang.InstantiationException, java.lang.NoSuchFieldException {
        Class<?> type = new ByteBuddy()
                .subclass(Object.class)
                .name("MyClassName")
                .defineMethod("custom", String.class, Modifier.PUBLIC)
                .intercept(MethodDelegation.to(Bar.class))
                .defineField("x", String.class, Modifier.PUBLIC)
                .make()
                .load(
                        getClass().getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();

        Method m = type.getDeclaredMethod("custom", null);
        assertEquals(m.invoke(type.newInstance()), Bar.sayBar());
        assertNotNull(type.getDeclaredField("x"));
    }

    /**
     * 重定义存在的类
     */
    @Test
    public void overrideExistsClass() throws InstantiationException, IllegalAccessException, IntrospectionException, java.lang.InstantiationException {

        // 插件增强 ..
        ByteBuddyAgent.install();

        // 这也就说明了  类可以被加载也可以被卸载 ...
        new ByteBuddy()
                .redefine(Foo.class)
                .method(named("sayHelloFoo"))
                .intercept(FixedValue.value("Hello Foo Redefined ..."))
                .make()
                .load(Foo.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());

        System.out.println(Foo.class.newInstance().sayHelloFoo());
    }
}
