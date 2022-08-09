package club.smileboy.app;

import club.smileboy.app.method.proxy.Bar;
import club.smileboy.app.method.proxy.Foo;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.Test;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * @author FLJ
 * @date 2022/8/9
 * @time 17:10
 * @Description 基于方法代理和自定义方法代理 ..
 */
public class MethodProxyTests {

    @Test
    public void methodProxy() throws InstantiationException, IllegalAccessException {
        System.out.println(new ByteBuddy()
                .subclass(Foo.class)
                .method(named("sayHelloFoo").and(isDeclaredBy(Foo.class)).and(returns(String.class)))
                .intercept(MethodDelegation.to(Bar.class))
                .make()
                .load(MethodProxyTests.class.getClassLoader())
                .getLoaded()
                .newInstance()
                .sayHelloFoo());
    }
}
