package club.smileboy.app.tutorial;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.agent.ByteBuddyAgent;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.loading.ClassReloadingStrategy;
import net.bytebuddy.pool.TypePool;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * @author FLJ
 * @date 2022/8/10
 * @time 13:47
 * @Description 尝试创建一个类 ...
 */
public class CreateClassTests {
    /**
     * 示例 类加载
     * <p>
     * 这里通过WRAPPER 策略加载一个类(对于大多数情况来说,这是合适的)
     * <p>
     * 通过getLoaded 方法即可获取已经加载的动态类 ...
     * 注意到,当加载了class的情况下,预定义的类加载策略 通过应用 当前执行上下文的ProtectionDomain 执行 ...
     * 除此之外,所有的默认策略提供了显式的保护域的规范(通过调用withProtectionDomain方法) ...
     * 定义一个显式的protection domain 式非常重要的(当使用security manager的情况下) 或者当与定义在签名的jar中的类协同工作时这很重要 ...
     */
    @Test
    public void test() {
        final Class<?> loaded = new ByteBuddy()
                .subclass(Object.class)
                .make()
                .load(getClass().getClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();

    }

    /**
     * reloading a class
     * 之前我们已经了解了如何重定义或者变基存在的类 ,在java 程序执行的期间, 它经常不可能保证指定的类已经加载了 (因此,Byte Buddy 当前仅仅将加载的类作为它的参数,这将在未来发生改变,
     * 当现有的API 能够被用来与未加载的类一同工作时) 感谢Java 虚拟机的HotSwap 特性,存在的类能够被重定义(甚至它就算被加载过),这个特性能够通过Byte Buddy的ClassReloadingStrategy 进行访问
     * 让我们来重定义一个类来说明这个策略 ...
     * <p>
     * 我们先定义两个类,然后将Foo类变为 Bar, 使用HotSwap ,我们能够将重定义使用在已经加载的类上 ...
     * <p>
     * HotSwap 仅仅能够通过 Java Agent 访问,例如一个代理能够在jvm 启动的时候通过 --javaagent参数安装它,或者 如果Java 应用是从JDK的 jvm启动,那么Byte Buddy能够直接在
     * 应用启动之后加载Java agent(通过调用ByteBuddyAgent.installOnOpenJDK()) ..
     * 因为类的重定义大多数情况被用来实现工具或者测试,这可能是非常方便的 ... 从java 9开始,一个代理安装可能直接从运行时安装而无需一个安装的JDK(本质上就是利用jdk的Jvm运行时)
     * 使用Java的HotSwap特性,这里却有巨大的缺陷,当前的HotSwap 的实现需要重定义的类采用相同的schema(在类的重定义前后,这是必须条件).. 这就意味着当重新加载一个类的时候无法增加方法或者字段 ..
     * 我们已经讨论过Byte Buddy 定义了原始方法的拷贝(对于任何变基的类,这样的类不能够与ClassReloadingStrategy工作) ..
     * 同样类的重定义对于使用了显式的初始化器的方法(在一个类中的静态代码块)的类 不与ClassReloadingStrategy 协同工作,因为这些初始化器同样被拷贝的一个额外的方法 ..
     * 不幸的是OpenJDK 已经从扩展HotSwap功能中撤离(也就是取消了研究), 因此这没有一种方式能够规避这种限制(当使用HotSwap特性),此时,Byte Buddy的HotSwap支持能够使用在极端情况下(它可能特别有用) ..
     * 否则类变基以及重定义能够是一个非常方便的特性(当对一个存在的类进行增强时,这是一个方便的特性,例如构建脚本 - gradle) ...
     * <p>
     * 总结,也就是类变基  / 重定义 能够增强 已有类的功能 ...
     * 但是这有致命缺陷,如果类的结构不一致,是无法发生重定义的 ...(也就是不能够修改方法签名 .... 等等内容,限制很多) ..
     */
    @Test
    public void reloadClass() {


        class Bar {
            String m() {
                return "bar";
            }
        }
        class Foo {
            String m() {
                return "foo";
            }

           /* String t() {
                return "123123";
            }*/
        }


        ByteBuddyAgent.install();

        final Foo foo = new Foo();
        foo.m();
        new ByteBuddy()
                // 也就是虽然我们将Foo 变为Bar,但是是重定义的 Bar,其实也可以说将Bar变成Foo ...
                .redefine(Bar.class)
                .name(Foo.class.getName())
                .make()
                .load(Foo.class.getClassLoader(), ClassReloadingStrategy.fromInstalledAgent());

        System.out.println(foo.m());

    }

    /**
     * 有人可能认为变基和重新定义指令的唯一有意义的应用是在构建期间 ...
     * 我们可以断言，一个被处理的类在其初始类加载之前没有被加载，仅仅是因为这个类的加载是在JVM的不同实例中完成的。
     * ByteBuddy 完全可以和没有加载的类工作 ,对此
     * ByteBuddy 基于java的反射API 进行抽象(例如一个类的示例,举个例子它能够内部的通过TypeDescription实例进行呈现) ..
     * 一个已知的事实是,Byte Buddy 仅仅知道如何处理一个提供的Class(通过一个实现了TypeDescription接口的适配器),这个抽象的大的优势是 (类上的信息不需要通过类加载器提供,它可以来自其他来源) ..
     * Byte Buddy 提供一种获取一个类的TypeDescription的持有典型方式(使用TypePool),一个这样的池的默认实现(被默认携带),也就是TypePool.Default 实现会解析一个类的二进制形式并且通过TypeDescription
     * 呈现它 .. 类似于 ClassLoader 它维护了一个呈现类的缓存(它能够被定制), 同样,它通常抓取一个类的二进制形式(从类加载器中),然而无需指示去加载这个类 ...
     *
     * java 虚拟机仅仅在第一次使用的时候加载class,因此,我们能够安全的重定义一个类:
     * 在程序启动运行任何其他代码之前:
     *
     * 但是这种方式,仅仅是创建一种新的类出来,这对于我们来说,不是想要的,假设我们需要根据用户的输入定义生成Class A,这样一直加 类膨胀不是 ???
     * (这个疑问已经消除,下面这个例子是正确的) ... 详情查看 https://github.com/diguage/byte-buddy-tutorial/issues/19  / https://github.com/raphw/byte-buddy/issues/1293
     *
     * 在这个例子中,通过 系统类加载器获取 类路径上的类的信息 ... 因为对于这些未加载的类,我们需要通过指定ClassFileLocator 查找 ..
     */
    @Test
    public void workingWithUnloadedClasses() {
//        new Bar();
        ByteBuddyAgent.install();
        final TypePool typePool = TypePool.Default.ofSystemLoader();
        Class<?> bar = null;
        try {
            bar = new ByteBuddy()
                    .redefine(typePool.describe("club.smileboy.app.tutorial.Bar").resolve(), // do not use 'Bar.class'
                            // 通过系统类加载器 获取它(但是没有让它去加载它,在这个示例中, 是SystemLoader)
                            ClassFileLocator.ForClassLoader.ofSystemLoader())
                    .defineField("qux", String.class) // we learn more about defining fields later
                    .name("club.smileboy.app.tutorial.Bar")
                    .make()
                    // 通过反射处理掉 ...
                    .load(ClassLoader.getSystemClassLoader(),ClassLoadingStrategy.Default.INJECTION).getLoaded();
            final Field qux = bar.getDeclaredField("qux");
            System.out.println(qux.getName());
        }catch (Exception e) {
            // pass
            System.out.println("有异常" + e.getMessage());
        }
        System.out.println("触发");

        final Class<Bar> barClass = Bar.class;
        try {
            final Field qux = barClass.getDeclaredField("qux");
            System.out.println(qux.getName());

        }catch (Exception e) {
            // pass
            System.out.println("没有这个 字段");
        }
        if(bar != null) {
            System.out.println("执行");
            for (Field declaredField : bar.getDeclaredFields()) {
                System.out.println(declaredField.getName());
            }

            System.out.println(bar.getName());
            System.out.println(Bar.class.getName());

            System.out.println(Bar.class.isAssignableFrom(bar));
            System.out.println(bar.getClassLoader().getName());
        }


        // 这个时候,如果我们再次重定义这个类,那么类加载器不会接收 ..
        // 不管你使用什么方式 ...
        final Class<?> value = new ByteBuddy()
                .redefine(typePool.describe(Bar.class.getName()).resolve(), ClassFileLocator.ForClassLoader.ofSystemLoader())
                .defineField("value", String.class, Modifier.PUBLIC)
                .make()
                .load(ClassLoader.getSystemClassLoader(), ClassReloadingStrategy.fromInstalledAgent())
                .getLoaded();

    }

    /**
     * 当一个应用成长的更加巨大的时候,它们具有更多的模块化,在特定的程序点中使用这样的 改造是一个繁琐的强制限制 ..
     * 因此这的确是一个更棒的方式应用这样的类定义(按需) .... 使用Java 代理 https://docs.oracle.com/javase/8/docs/api/java/lang/instrument/package-summary.html
     * 它可能直接拦截任何类的加载活动(在java 应用程序中引导的) ...
     * 一个Java 代理是通过简单的jar 文件实现的(具有入口,它指定在jar的清单文件中,如果链接所指向的 那样) ...
     * 使用Byte Buddy,一个代理的实现是非常直观的(通过使用AgentBuilder),假设我们之前定义了一个简单的名为ToString的注解,它能够简化去实现toString 方法(通过为所有注解了此注解的类), 它的实现逻辑通过
     * 实现Agent的premain 方法: ...(premain 我认为是预处理的形式 ...)
     * 创建Java 代理
     */
    @Test
    public void createJavaAgent() {
        // 查看 java-agent的模块了解更多 ..
    }

    /**
     * android使用不同的类文件形式(它们使用 dex 文件)这并不是java 类文件的形式 ...
     * 因此,使用ART 运行时(它继承了Dalvik virtual machine),andriod 应用能够在安装到andriod 设备之前编译代码为 本地机器码 ..
     * 只要应用程序未与其 Java 源代码一起显式部署，Byte Buddy 就不能再重新定义或重新定义类，因为没有中间代码表示可以解释。
     *
     * Byte Buddy 仍然能够使用DexClassLoader 去定义一些新的类(通过使用内置的dex 编译器), 对此,Byte Buddy 提供了byte-buddy-android 模块
     * 它包含了AndroidClassLoadingStrategy(这允许动态的加载创建的类),为了实现这个功能,它需要一个目录去写入一些临时文件以及编译的类文件 ...
     * 这个目录必须不能够在不同的应用之间共享(因为 Android 的安全管理器禁止这样做。)
     */
    @Test
    public void  loadingClassesInAndroidApplications() {

    }

    /**
     * Byte Buddy 能够处理泛型类型, 泛型并没有被java 运行时考虑(它仅仅考虑泛型的擦除)
     * 然而,泛型仍然能够嵌入到任何java 类文件中并且能够通过java 反射API 进行暴露,因此它有时对于包含通用信息到生成类是有用的(因为这些泛型信息能够影响其他库和框架的行为) ..
     * 内嵌泛型信息是非常重要的(当一个类被java 编译器持久化和处理的时候) ..
     *
     * 当继承一个类的时候,实现一个接口或者声明一个字段或者方法的时候,Byte Buddy 接受一个Java 类型而不是一个擦除的Class(对于上述原因) ... 泛型能够显式的通过TypeDescription.Generic.Builder进行定义 ..
     * Java泛型和类型擦除的重要之一的区别是类型变量的上下文含义 ... 由某些类型定义的特定名称的类型变量并不代表着相同类型(当使用相同的名称为 另一个类型声明相同的类型变量时) ..
     * 因此 Byte Buddy 在生成的类型或者方法的上下文中重新绑定了所有泛型(表示类型变量的泛型)(当一个Type 实例被这个库(Byte Buddy)所处理的时候)
     *
     * Byte Buddy 也会插入 bridge methods(透明的) - 当一个类型被创建的时候 ..
     * Bridge 方法能够通过 MethodGraph.Compiler 解析,它是一个ByteBuddy实例的属性 .. 默认的方法图编译器行为类似于java 编译器并且能够处理任何类文件的泛型类型信息,对比其他语言,不同的语言图编译器可能是更合适的 ...
     */
    @Test
    public void workingWithGenericTypes() {

    }

}
