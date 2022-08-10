package club.smileboy.app;

import club.smileboy.app.method.proxy.Foo;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.NamingStrategy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.Test;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Modifier;

/**
 * @author JASONJ
 * @date 2022/8/9
 * @time 21:49
 * @description ByteBuddy 基于约定大于配置的形式 ...
 * 我们能够对继承的子类型(动态类型)进行约定命名,默认情况下,对于从java.lang包下继承的类,命名不能够放在这个命名空间下 ..
 * 其他情况下,ByteBuddy 会将命名 放在父类相同包下(这样 父类的包可见方法对动态类型可见,但是这个特性无所谓,你作为子类肯定能够访问 ..) ...
 *
 * 领域特定语言和不变性,通过链式调用你能够正确的生成 动态类型,但是如果你使用以下方式调用,很显然不变性导致你产生错误的动态类型
 * ByteBuddy byteBuddy = new ByteBuddy();
 * byteBuddy.withNamingStrategy(new NamingStrategy.SuffixingRandom("suffix"));
 * DynamicType.Unloaded<?> dynamicType = byteBuddy.subclass(Object.class).make();
 *
 *
 * 1. 重新定义
 *      类型重定义
 * 2. 覆盖已有定义
 **/
public class ByteBuddyMain {
    /**
     * hello world
     * @param args
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
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

    /**
     * class type naming strategy
     * 默认情况以 net.bytebuddy.renamed命名
     */
    @Test
    public void subDynamicType() {
        DynamicType.Unloaded<Object> dynamicType = new ByteBuddy()
                .subclass(Object.class)
                .make();

        DynamicType.Loaded<Object> load = dynamicType.load(ClassLoader.getSystemClassLoader());
        // net.bytebuddy.dynamic.DynamicType$Default$Loaded
        System.out.println(load.getLoaded().getName());
    }

    @Test
    public void subCustomNameDynamicType() {
        DynamicType.Unloaded<Object> app = new ByteBuddy()
                .subclass(Object.class)
                .name("club.smileboy.app.AppObject")
                .make();

        DynamicType.Loaded<Object> load = app.load(ClassLoader.getSystemClassLoader());
        System.out.println(load.getLoaded().getName());
    }

    @Test
    public void subCustomNameByStrategyDynamicType() {
        DynamicType.Unloaded<Object> make = new ByteBuddy()
//                .with(new NamingStrategy.AbstractBase() {
//                    @Override
//                    protected String name(TypeDescription superClazz) {
//                        return "javax.mlnlco.app." + superClazz.getSimpleName();
//                    }
//                })
                // 还可以通过 后缀随机的方式 设计一种不会冲突的类型 ..
                // Java 虚拟机就是使用名字来区分不同的类型的，这正是为什么要避免命名冲突的原因。如果你需要定制命名行为，请考虑使用 Byte Buddy 内置的 NamingStrategy.SuffixingRandom，你可以通过引入比默认对你应用更有意义的前缀来定制命名行为
                .with(new NamingStrategy.SuffixingRandom("dynamicType","club.smileboy.app"))
                .subclass(Object.class)
                .make();
        DynamicType.Loaded<Object> load = make.load(ClassLoader.getSystemClassLoader());
        System.out.println(load.getLoaded().getName());

    }


    // 类型重定义
    public static class TypeReDefineTests {

        @Test
        public void test() throws IntrospectionException {
            ByteBuddyAgent.install();
            BeanInfo beanInfo = Introspector.getBeanInfo(Foo.class);
            PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();


            // 对于一个已经加载的类,不能够冲顶
            DynamicType.Unloaded<Foo> value = new ByteBuddy()
                    .redefine(Foo.class)
                    .defineField("value", String.class, Modifier.PROTECTED)
                    .make();

        }

    }

}
