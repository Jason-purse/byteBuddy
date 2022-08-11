package club.smileboy.app;

import club.smileboy.app.method.proxy.Bar;
import club.smileboy.app.method.proxy.Foo;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.NamingStrategy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.dynamic.scaffold.InstrumentedType;
import net.bytebuddy.implementation.*;
import net.bytebuddy.implementation.auxiliary.MethodCallProxy;
import net.bytebuddy.implementation.bytecode.ByteCodeAppender;
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
 * 如何增强已经存在的类:
 * 1. 类型重定义
 *      允许修改存在类的修改,通过增加字段以及方法或者替代存在的方法实现 ,之前的方法实现将会丢失(如果通过其他实现进行替换) ..
 *      class Foo {
 *          String bar() {return "bar";}
 *      }
 *      为了从bar方法返回 "qux",返回原来"bar"的方法将会完全丢失 ...
 *      也就是类型重定义,仅仅能够修改已有信息 ...(也就是replace的语义) ...
 * 2. 类型变基(基底)
 *    当变基一个类的时候,ByteBuddy 会保留基底的类的方法实现 .. 而不是像执行类型重定义那样抛弃覆盖的方法  ..
 *    ByteBuddy 会覆盖所有的方法实现 并使用兼容的签名保留(且私有), 这种方式不会丢失任何实现并且变基的方法能够继续执行原来的代码,通过调用这些重命名的方法,例如一个之前Foo 类变基之后,如下所述
 *    class Foo {
 *        String bar() {return "foo" + bar$original() }
 *        private String bar$original() {
 *            return "bar";
 *        }
 *    }
 *    当变基一个类的时候,ByteBuddy 对待所有的方法定义就像在定义一个子类,例如你能够调用变基的方法(如果你想尝试调用一个变基方法的父类方法实现),但是它
 *    将这种假象的超类扁平化到上面显示的变基的类型中 ...
 *
 *    在任何变基、重定义或者继承的形式都是用一种等价的API执行(它们通过DynamicType.Builder接口定义)  ..
 *    也就是说,它能够定义类作为子类并且在之后修改这个定义使用一个变基的类进行替代,这仅仅需要通过改变ByteBuddy的领域特定语言的一个单词即可实现 ..
 *    例如:
 *    subclass  / redefine / rebase ..
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
        public void test() throws IntrospectionException, InstantiationException, IllegalAccessException {
            ByteBuddyAgent.install();
            BeanInfo beanInfo = Introspector.getBeanInfo(Foo.class);
            PropertyDescriptor[] propertyDescriptors = beanInfo.getPropertyDescriptors();


            // 对于一个已经加载的类,不能够修改 class 的schema,也就是不能添加方法 / 修改方法签名 .. 增加字段 .. 拥有诸多限制 ..
            final DynamicType.Loaded<Foo> sayHelloFoo = new ByteBuddy()
                    .redefine(Foo.class)
                    .method(ElementMatchers.named("sayHelloFoo"))
                    .intercept(MethodDelegation.to(Bar.class))
                    .make()
                    .load(ClassLoader.getSystemClassLoader(), ClassReloadingStrategy.fromInstalledAgent());

            System.out.println(sayHelloFoo.getLoaded());
            sayHelloFoo.getLoaded().newInstance().sayHelloFoo();

        }

    }

    @Test
    public void rebaseTests() {
        new ByteBuddy()
                .rebase(Foo.class)
                .method(ElementMatchers.named("sayHelloFoo"));
//                .intercept()
    }

}
