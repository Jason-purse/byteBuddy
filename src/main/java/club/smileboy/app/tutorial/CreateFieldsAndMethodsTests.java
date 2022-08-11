package club.smileboy.app.tutorial;

import club.smileboy.app.method.proxy.Foo;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

import static net.bytebuddy.matcher.ElementMatchers.*;

/**
 * @author FLJ
 * @date 2022/8/11
 * @time 11:14
 * @Description byte Buddy fields 以及 methods 支持 ...
 *
 * 大多数我们在前面的部分创建的类型 都没有定义任何字段或者方法 , 然而继承一个Object,创建的类继承的这些方法通过它的父类进行定义,让我们验证这个Java琐事 并在动态类型上调用toString 方法 ...
 * 我们能够获取任何一个实例(通过调用创建类的构造器(反射性的调用)) ...
 *
 * 创建的动态类有能力定义一些新的逻辑, 为了说明它是怎么做的,让我们做一些简单的事情,我们想要覆盖 toString 方法并返回 "Hello world" 而不是之前的默认值 ...
 */
public class CreateFieldsAndMethodsTests {

    @Test
    public void subClassByTests() throws InstantiationException, IllegalAccessException {
        String s = new ByteBuddy()
                .subclass(Object.class)
                .name("example.Type")
                .make()
                .load(getClass().getClassLoader())
                .getLoaded()
                .newInstance().toString();

    }

    /**
     * 这个例子中我们使用了两种 byte buddy的领域特定语言指令
     * 1. method(表示选择任意数量的方法进行覆盖)
     * 2. named(进行方法筛选) ...
     *
     * named("toString").and(returns(String.class)).and(takesArguments(0));
     *
     * 后面这个方法匹配通过完全Java 签名的toString 方法(因此它仅仅匹配特定的方法) ..
     * 然而,在这个给定的上下文,我们知道这里没有名为toString的不同签名方法(因此我们的原来的方法匹配器是满足的) ...
     * 在选择了toString 之后,我们通过第二个指令(intercept)决定这个实现应该覆盖给定的选择,在上面的例子中,我们使用FixedValue实现 ..
     *
     * 对于方法的匹配规则:
     * byte buddy 通过栈来约束,越后面的规则越靠近栈顶,那么规则会更快使用 ...
     * 一般来说越不明确的规则应该越先定义,然后不断的减少查询的范围,否则后续定义的不明确的规则可能会打破你的规则匹配(出乎你的意想之外) ...
     * 除此之外,byte buddy 允许配置一个ignoreMethod属性,对于匹配了方法匹配器的方法将不会覆盖(默认情况,byte buddy 不会覆盖任何合成方法) ..
     *
     * 某些情况你可能想要定义一些新的方法,但是不覆盖超类的方法或者一个接口... byte buddy可以实现,对此你只需要调用 defineMethod(通过定义方法签名) ,
     * 在定义了方法之后,你能够去提供一个Implementation(就像通过方法匹配器表示的等价方法实现) ...
     * 注意的时候,越后注入的方法(可能会覆盖之前的实现定义 ,根据之前的堆栈原理) ...
     * 当然defineField,允许为给定类型定义字段,在Java中,字段绝不会覆盖(但是能够被遮蔽),对于这个原因,没有字段匹配方式或者类似于这种形式可用 ...
     * @throws InstantiationException
     * @throws IllegalAccessException
     */
    @Test
    public void replaceMethodByTests() throws InstantiationException, IllegalAccessException {
        System.out.println(new ByteBuddy().subclass(Object.class)
                .name("example.Type")
                .method(named("toString"))
                .intercept(FixedValue.value("Hello world !!"))
                .make()
                .load(getClass().getClassLoader())
                .getLoaded()
                .newInstance()
                .toString());
    }

    /**
     * 上面的规则如下
     */
    @Test
    public void rulePushStackByTests() throws InstantiationException, IllegalAccessException {
        class Foo {
            public String bar() { return null; }
            public String foo() { return null; }
            public String foo(Object o) { return null; }
        }

        Foo dynamicFoo = new ByteBuddy()
                .subclass(Foo.class)
                // 最后匹配它
                .method(isDeclaredBy(Foo.class)).intercept(FixedValue.value("One!"))
                // 其次匹配它
                .method(named("foo")).intercept(FixedValue.value("Two!"))

                // 最先匹配
                .method(named("foo").and(takesArguments(1))).intercept(FixedValue.value("Three!"))
                .make()
                .load(getClass().getClassLoader())
                .getLoaded()
                .newInstance();
    }

    /**
     * 仔细的查看固定值 ..
     *
     * 固定值简单的返回给定的对象,一个类能够记住一个对象(通过不同的方式)
     * 1. 写入到 类的常量池
     *    常量池是Java 类文件格式中的一部分并且它包含了大量无状态值(描述任何类的属性),这个常量池主要是记住类的属性(例如
     *    类的名称以及 方法的名称), 除了这些自省性的属性,这个常量池包含了一个房间(来存储任何字符串或者基础类型的值(它们可能使用在方法中或者类的字段中)) ..
     *    除了字符串和基础类型值 ... (类的常量池还能存储其他类型的引用) ..
     * 2. 这个值存储在一个类的静态字段中,为了这样做,这个字段必须分配给定的值(一旦类加载到 jvm中),对此,每一个动态创建的类是通过TypeInitializer 完成的
     *      这能够被配置去执行(例如显式的初始化) ... 当你指示一个DynamicType.Unloaded 被加载,Byte Buddy 自动的触发它的类型初始化器,这样这个类就可以准备好使用 ..
     *      因此你不需要担心这些类型初始化器 ..
     *      然而如果你想要加载动态类(它们在Byte Buddy之外加载), 重要的是你可以在这些类加载之后手动的运行这些类型初始化器 ..
     *      否则 FixedValue 实现能够(例如 返回null 而不是这个必须的值,因为这个静态字段绝不会分配到这个值)返回特定值 ..
     *      许多动态类型可能不需要显式的初始化.. 可以通过调用其 isAlive 方法来查询类的类型初始值设定项的活跃度 ...(也可以根据是否初始化来了解 具体值信息)
     *      如果你需要手动的触发TypeInitializer ,你能够通过DynamicType 接口能够使用 ..
     *
     *      当你通过FixedValue#value(Object)实现的时候,Byte Buddy 会分析参数类型并且将它定义到动态类型的类常量池中(如果可能),否则将这个值存储到静态字段中 ....
     *      注意到这是针对选择的方法所返回的实例(可能会是不同的对象身份 - 如果这个值是存储在类的常量池中) ... 因此你能够指示Byte Buddy 总是存储一个对象到静态字段上(通过使用
     *      FixedValue#reference(Object) ... 后者将是重载的(这样你能够提供字段的名称作为第二个参数),否则字段名将自动从对象的hash code码上进行自动衍生,这个行为的异常情况是 value = null ...
     *      这个 null值绝不会存储到字段中 但是它将简单的通过文本表达式呈现 ...
     *
     *      你可能怀疑这个上下文的类型安全性, 你能够定义一个方法返回一个无效的值 ...
     *
     *      如下代码是错误的,在java 类型系统中很难通过编译器阻止无效的实现 ... 相反 Byte Buddy将抛出一个IllegalArgumentException ...
     *      如果参数不一致,Byte Buddy 尝试最好的确保它所创建的类型是遗留的Java 类型并且在创建一个无效类型的期间抛出一个异常而快速失败 ...
     *
     *      // 没有示例,展示无法体验到什么东西
     *      ByteBuddy的分配行为是可定制的,对此,Byte Buddy 仅仅提供了一个默认实现 它模拟这个java 编译器的分配行为 ..
     *      因此 Byte Buddy 允许一个类型分配到任何一个它的父类型并且它也能够考虑 装箱 基础类型值或者拆箱它们的包装器呈现 ... 注意到:
     *      Byte Buddy 当前并没有完全支持泛型类型并且仅仅考虑类型擦除 .. 因此它可能会导致堆污染 ... 而不是使用一个预定义的分配器,你能够总是实现自己的Assigner,
     *      它能够进行 Java 编程语言中不隐含的类型转换,我们将查看这样的自定义实现(在教程的末尾) .. 对此,我们先在FixedValue实现上 调用withAssigner 自定义分配器 ...
     */
    @Test
    public void closerLookFixedValues() {
            new ByteBuddy().subclass(Foo.class)
                    .method(isDeclaredBy(Foo.class))
                    .intercept(FixedValue.value(0))
                    .make();
    }



    /**
     * 代理一个方法的调用
     * 在很多场景中,返回一个固定值这是不满足的,为了更加灵活,Byte Buddy 提供了MethodDelegation 实现这提供了最大的自由行和方法调用交互 ...
     * 一个方法代理定义了动态创建的类型的方法 转发到另一个方法的调用,这可能存活在动态类型之外 , 这种形势下,一个动态类型的逻辑能够被呈现(通过使用纯java表示,但是绑定到另一个方法实现是需要通过代码生成的) ..
     *
     *
     * 如果出现 类加载出现问题,建议重新将项目构建一下 ...  可能导致的缓存 让byte Buddy 使用
     */
    @Test
    public void delegatingMethodCall() throws InstantiationException, IllegalAccessException {

        System.out.println(new ByteBuddy()
                .subclass(Source.class)
                                .method(named("helloWorld"))
                                .intercept(MethodDelegation.to(Targeted.class))
                .defineField("ttt", String.class, Modifier.PUBLIC)
                .make()
                .load(ClassLoader.getSystemClassLoader())
                .getLoaded()
                .newInstance().helloWorld("world"));
//                .hello("world");

    }

    /**
     * 默认 byte buddy 像 java 编译器一样处理方法重载,会寻找更加对应具体类型的方法,
     * 代理目标方法的方法不需要和和代码方法同名,  参数分配也可以可定制的,你可以指定目标方法的参数对应顺序(通过@Argument 参数进行映射) ...
     *
     * 如果没有显式的定义,那么 默认情况 void foo(Object o1, Object o2)
     * 等价于 void foo(@Argument(0) Object o1, @Argument(1) Object o2) ...
     * 如果方法签名无法对应,那么方法将会抛弃(也就是无法对应,最终没有找到合适的,那么就抛出异常) ..
     * 除了@Argument 注解,这里有各种预定义的注解能够与MethodDelegation 结合使用 ...
     * - 参数能够携带@AllArguments 注解(必须包含一个数组类型并且对应包含所有原方法参数的数组) ...
     *    对此,所有的源方法参数必须能够对应到 数组的组件类型 ...
     *      如果不是这种情况,当前的目标方法将不考虑作为源方法的映射 ...
     * - @This 注解引起动态类型实例的分配(在当前执行的拦截的方法上) ..
     *   如果这个注解的参数无法分配到动态类型的实例 ... 当前方法不会考虑作为源方法的映射 ..
     *   注意到在这个实例上调用的任何方法将会导致最终的指示方法实现 ... 为了调用覆盖实现,你需要使用@Super注解,一个典型的原因是使用@This注解去更加细腻的访问实例字段 ...
     *
     * - 通过@Origin注释的参数 必须使用在Method / Constructor , Executable,Class,MethodHandle,MethodType,String 或者int上,依赖于参数类型,它能够分配方法或者构造器引用原来的方法或者构造器(当前正在检测的,或者引用一个动态创建的Class)
     *   当使用Java 8,它也可能接收要么一个方法或者一个构造器引用(通过在拦截器中使用Executable),如果注解参数是一个String,那么这个参数将分配为Method的toString方法的返回值 ...
     *   通常,我们推荐使用这些String 值作为方法的标识符(不推荐使用Method对象,因为它们的查询引入了极大的运行时消耗),为了避免这种消耗,@Origin 注解提供了一个属性来缓存这些实例能够进行重用 ...
     *   注意到MethodHandle 以及 MethodType 存储在 类的常量池中(但是使用这些常量池的类必须至少是java 7版本以上) ..
     *   而不是使用反射去在另一个对象上反射性的执行 拦截的方法 ... 我们未来推荐使用@Pipe 注解,当使用@Origin 注解在int 类型的参数上,它能够分配检测方法的修饰符 ...
     *
     * 除了使用这些预定义的注解,byte buddy 允许你定义你自己的注解(通过注册一个或者多个ParameterBinder即可),在教程最后查看这些自定义 ...
     * 除了这4个注解,还有两种额外的预定义注解 能够授权访问动态类型的超类实现方法 ... 这种方式是,举个例子你能够增加一个切面到一个类(例如日志记录方法调用,使用@SuperCall 注解,一个超类实现的方法调用能够执行,即使它来自动态类外部 ...
     *
     * 在实际测试的过程中,发现了一个问题 , 对应的issues在 https://github.com/raphw/byte-buddy/issues/199 ...
     *
     * 同样,注意到 @SuperCall 注解也能够使用在Runnable上,如果原始的方法返回值为空(那么可以丢弃掉) ...
     */
    @Test
    public void methodProxyHandleTests() throws InstantiationException, IllegalAccessException {
        new ByteBuddy()
                .subclass(MemoryDatabase.class)
                .method(named("load"))
                .intercept(MethodDelegation.to(LoggerInterceptor.class))
                .make()
                .load(ClassLoader.getSystemClassLoader())
                .getLoaded()
                .newInstance()
                .load("你好");

    }

    @Test
    public void imitateConstructor() throws InstantiationException, IllegalAccessException {
        new ByteBuddy()
                // 伪造超类 open
                .subclass(RunnerSource.class,ConstructorStrategy.Default.IMITATE_SUPER_CLASS_OPENING)
                .method(named("printf"))
                .intercept(MethodDelegation.to(RunnerTarget.class))
                .make()
                .load(getClass().getClassLoader())
                .getLoaded()
                .newInstance()
                .printf("data");
    }




    static class RunnerSource {

        public void printf(String data) {
            //
        }
    }

    static class RunnerTarget {

        public static void invoke(String data) {
            System.out.println("display result: " + data);
        }
    }
}
