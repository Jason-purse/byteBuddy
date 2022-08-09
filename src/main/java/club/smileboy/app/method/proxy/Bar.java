package club.smileboy.app.method.proxy;

import net.bytebuddy.implementation.bind.annotation.BindingPriority;

/**
 * @author FLJ
 * @date 2022/8/9
 * @time 17:17
 * @Description 当被代理的方法  变得歧义的时候,可以通过@BindingProperty 解决
 */
public class Bar {

    @BindingPriority(10)
    public static String sayHelloBar() {
        return "Holla in Bar!";
    }

    @BindingPriority(20)
    public static String sayBar() {
        return "say bar";
    }
}
