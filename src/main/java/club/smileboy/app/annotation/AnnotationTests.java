package club.smileboy.app.annotation;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.*;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;

import static net.bytebuddy.dynamic.loading.ClassLoadingStrategy.Default.INJECTION;
import static net.bytebuddy.matcher.ElementMatchers.named;

/**
 * @author FLJ
 * @date 2022/8/12
 * @time 13:51
 * @Description ByteBuddy 提供的一些注解使用 ...
 */
public class AnnotationTests {

    public static class DB {
        public String hello(String name) {
            System.out.println("DB:" + name);
            return null;
        }
    }


    public static class Interceptor {

        private Object target;

        public void setTarget(Object target) {
            this.target = target;
        }

        @RuntimeType
        public Object intercept(
                @This Object obj, // 目标对象(通过Object 随意的获取这个对象)
                @AllArguments Object[] allArguments, // 注入目标方法的全部参数
                @SuperCall Callable<?> zuper, // 调用目标方法，必不可少哦
                @Origin Method method, // 目标方法
                @Super DB db // 目标对象的辅助对象 ...
                ,
                @Morph OverrideCallable overrideCallable
        ) throws Exception {
            System.out.println("this " + obj);
            System.out.println("super " + db);
            // 从上面两行输出可以看出，obj和db是一个对象(错, this 是目标对象),而 db是目标对象的辅助对象 ...(它实现了目标对象的所有接口, 覆盖了实现,用于调用父类方法) ...
            try {
                System.out.println("处理");
                return zuper.call(); // 调用目标方法
            } finally {
            }
        }
    }
    // 能够覆盖调用的参数 ...
    public interface OverrideCallable {
        Object call(Object[] args);
    }

    @Test
    public void annotationTest() throws InstantiationException, IllegalAccessException {
        Interceptor interceptor = new Interceptor();
        DB hello = new ByteBuddy()
                .subclass(DB.class)
                .method(named("hello"))
                // 拦截DB.hello()方法，并委托给 Interceptor中的静态方法处理
//                .intercept(MethodDelegation.to(Interceptor.class))
                .intercept(MethodDelegation.withDefaultConfiguration().withBinders(Morph.Binder.install(OverrideCallable.class))
                        .to(interceptor))
                .make()
                .load(ClassLoader.getSystemClassLoader(), INJECTION)
                .getLoaded()
                .newInstance();
        System.out.println("origin object " + hello);

        interceptor.setTarget(hello);
        String helloWorld = hello
                .hello("World");
        System.out.println("result " + helloWorld);
    }
}

