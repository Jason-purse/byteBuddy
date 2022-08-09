package club.smileboy.app;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.matcher.ElementMatchers;

public class ByteBuddyMain {
    public static void main(String[] args) throws InstantiationException, IllegalAccessException {
        DynamicType.Unloaded<Object> unloadedType = new ByteBuddy()
                .subclass(Object.class)
                .method(ElementMatchers.isToString())
                .intercept(FixedValue.value("Hello World ByteBuddy!"))
                .make();

        // 通过指定类加载器将字节码加载
        final Class<?> ObjectClazz = unloadedType.load(ByteBuddyMain.class.getClassLoader())
                .getLoaded();

        System.out.println(ObjectClazz.newInstance());

    }
}
