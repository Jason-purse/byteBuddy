package club.smileboy.app.tutorial;

import club.smileboy.app.method.proxy.Foo;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.ClassFileVersion;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.dynamic.scaffold.subclass.ConstructorStrategy;
import net.bytebuddy.implementation.*;
import net.bytebuddy.implementation.bind.annotation.BindingPriority;
import net.bytebuddy.implementation.bind.annotation.Pipe;
import net.bytebuddy.implementation.bind.annotation.Super;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.matcher.ElementMatchers;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;

import java.nio.channels.AsynchronousFileChannel;
import java.nio.channels.FileChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

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
     * // 但是这个有点坑,还是需要 修饰符为 public
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

    /**
     * 你可能还是想要支持 这些辅助类型为什么能够调用其他类型的父类方法(这通常在Java中是完全屏蔽的,不可取) ..
     * 它到底是如何做的 ...
     * 本质上它如下:
     *  class LoggingMemoryDatabase extends MemoryDatabase {
     *
     *   private class LoadMethodSuperCall implements Callable {
     *
     *     private final String info;
     *     private LoadMethodSuperCall(String info) {
     *       this.info = info;
     *     }
     *
     *     @Override
     *     public Object call() throws Exception {
     *       return LoggingMemoryDatabase.super.load(info);
     *     }
     *   }
     *
     *   @Override
     *   public List<String> load(String info) {
     *     return LoggerInterceptor.log(new LoadMethodSuperCall(info));
     *   }
     * }
     *
     * 有些时候,
     *
     * @throws InstantiationException
     * @throws IllegalAccessException
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     */
    @Test
    public void imitateConstructor() throws InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        new ByteBuddy()
                // 伪造超类 open
                .subclass(RunnerSource.class)
                .method(named("printf"))
                .intercept(MethodDelegation.to(RunnerTarget.class))
                .make()
                .load(getClass().getClassLoader())
                .getLoaded()
                .getDeclaredConstructor()
                .newInstance()
                .printf("你好");
    }




    public static class RunnerSource {

        public void printf(String data) {
            System.out.println("父类实现 ...");
        }
    }

    public static class RunnerTarget {

        public static void invoke(@SuperCall Runnable runnable) {
            System.out.println("调用父类实现方法");
            runnable.run();
            System.out.println("调用结束");
        }
    }

    static class ChangingLoggerInterceptor {
        public static List<String> log(String info, @Super MemoryDatabase zuper) {
            System.out.println("Calling database");
            try {
                return zuper.load(info + " (logged access)");
            } finally {
                System.out.println("Returned from database");
            }
        }
    }

    /**
     * 有时候我们想要调用父类中的不同参数的方法来表示原始方法的调用 ..
     * 通过@Super注解,byteBuddy 将会创建辅助类 它们会继承超类或者动态类型的接口,然后 覆盖动态类型中的所有方法去调用父类实现 ...
     *
     * class ChangingLoggerInterceptor {
     *             public static List<String> log(String info, @Super MemoryDatabase zuper) {
     *                 System.out.println("Calling database");
     *                 try {
     *                     return zuper.load(info + " (logged access)");
     *                 } finally {
     *                     System.out.println("Returned from database");
     *                 }
     *             }
     *         }
     *     }
     *
     *     现在这个被@Super 注解的参数是实际动态类型的实例的不同的身份 ..
     *     参数访问实例的字段并不 反应实际的实例字段 .. 辅助实例的不可覆盖方法 不会代理它们执行(代理到父类方法执行),而是保留原始实现,
     *     这可能导致慌缪的行为, 最终带有@Super 的注解的参数 并不是相关动态类型的超类(那么它们也不会作为任何方法的绑定实现)
     *
     *     由于这个@Super 它可以使用到任何的类型上,那么它可能需要提供一些信息用来构建实例 ..
     *     默认情况byteBuddy将会尝试使用类的默认构造器,总是对于隐式继承Object类型的接口这样做,然而当继承的是一个动态类型的父类,
     *     它有可能没有提供这个默认的构造器,这种情况下(或者我们可以使用指定的构造器时用来创建这样的辅助类型),@Super注解允许标识一个不同的构造器
     *     通过使用这个注解的constructorParameters设置 构造器的参数信息 .. 这个构造器将会在创建的时候调用并将对应的默认值分配到每一个参数 ..
     *
     *     除此之外,同样可以使用Super.Instantiation.UNSAFE 策略 用来创建类(它通过使用java 的内部类而需要调用 任何 的构造器来创建辅助类型) ..
     *     但是这个策略不兼容非Oracle得jvm 可能在未来也不再支持,但是目前而言,大多数jvm实现都能找到这个不安全得实现策略 ...
     *
     *     我们可以知道LoggerInterceptor 它声明了一个检查异常 ... 但是原始方法没有声明任何检查异常,通常 java 编译器将拒绝编译这样的调用 ...
     *     但是对比编译器,java 运行时不会将检查异常和它们的检查异常区别对待并允许这个调用 ..
     *     但是当我们抛出 未声明的异常需要注意,不应该让用户疑惑 ...
     */
    @Test
    public void SuperOverrideMethodInvoke() {

    }

    /**
     * 注意到,方法代理模型 使用静态类型(这对于实现方法是非常好的) ..
     * 但是严格类型导致限制了代码的重用 ...
     *
     * class Loop {
     *     public String loop(String value) {return value;}
     *     public int loop(int value) {return value;}
     * }
     *
     * 上面的代码签名很类似但是 具有不同的类型参数,通常你可以不会使用一个拦截器去拦截多个方法,
     * 而是一个目标方法对应一个拦截器方法(仅仅是为了静态类型匹配) ..
     * 但是为了代码重用,byte buddy 允许给方法和方法参数注释注解,让byte buddy 暂停静态类型检查(更加喜爱运行时类型cast) ...
     *
     * class Interceptor {
     *   @RuntimeType
     *   public static Object intercept(@RuntimeType Object value) {
     *     System.out.println("Invoked method with: " + value);
     *     return value;
     *   }
     * }
     * 这种是有代价的,虽然我们现在可以用一个方法拦截两个方法调用 .. 但是牺牲了类型安全,随时可能会遇到类型强转异常 ...
     *
     * 作为一个等价物(@SuperCall的等价物) ... 你能够使用@DefaultCall注解,它允许 默认方法的执行而不是调用一个超类方法的执行   ..
     * 具有这个参数注解的方法将被考虑进行绑定(如果这个拦截的方法确实是这样) ...
     * 通过接口声明的默认方法将会直接被检查类型所实现,类似的,@SuperCall 注解会阻止方法绑定(如果指示的方法并没有定义一个非抽象的父类方法) ..
     * 如果你想执行一个指定类型的默认方法,你能够指定@DefaultCall的targetType property为特定的接口 ...
     * 通过这种约定,如果方法存在 ,Byte Buddy 将会注册一个代理实例(当执行给定接口类型的默认方法时) ...
     * 否则这个具有参数注解的目标方法将不考虑为代理目标 ,特别是默认方法调用仅仅对java 8或者更新的版本定义的类生效 ,类似的
     * 除了@Super 注解,这里的@Default注解将会注入一个代理用来显式的执行特定的默认方法 ....
     *
     * 另外我们可以通过任何MethodDelegation 注册或者定义自定义注解 ..
     * Byte Buddy 携带了一个可以使用的注解(但是需要显式注册) ..
     *
     * 你能够转发一个拦截的方法调用到另一个实例 ...
     *
     * @Pipe 注解需要显式注册(因为它在java 8之前声明,在java 8才引入了Function类型,所以我们需要自己提供自定义类型来表达函数 ..
     * 他需要一个OBject参数,返回一个Object结果 ...当然我们可以使用泛型,只要它能够被Object约束 ..
     *
     * 当执行方法的时候,Byte buddy 将根据方法的声明类型强转方法参数并且调用被拦截的方法(使用相同的参数) ..
     * interface Forwarder<T, S> {
     *   T to(S target);
     * }
     * 这样我们能够实现一个新的解决方式记录日志 通过转发一个方法调用到存在的实例 ..
     *
     * 本质上 它无法区分类型 .... 泛型信息丢失了 ... (刚开始学习,还有许多东西都是过了一遍 没有串起来)
     *
     */
    @Test
    public void strictTypeForMethodInvoke() throws InstantiationException, IllegalAccessException {
        System.out.println(new ByteBuddy()
                .subclass(MemoryDatabase.class)
                .method(named("load")).intercept(MethodDelegation.withDefaultConfiguration()
                        .withBinders(Pipe.Binder.install(Forwarder.class))
                        .to(new ForwardingLoggerInterceptor(new MemoryDatabase())))
                .make()
                .load(getClass().getClassLoader())
                .getLoaded()
                .newInstance()
                .load("你好"));
    }
    public interface Forwarder<T, S> {
        T to(S target);
    }
    public static class ForwardingLoggerInterceptor {

        private final MemoryDatabase memoryDatabase; // constructor omitted

        public ForwardingLoggerInterceptor(MemoryDatabase memoryDatabase) {
            this.memoryDatabase = memoryDatabase;
        }

        @BindingPriority(10)
        public List<String> log(@Pipe Forwarder<List<String>, MemoryDatabase> pipe) {
            System.out.println("Calling database");
            try {
                return pipe.to(memoryDatabase);
            } finally {
                System.out.println("Returned from database");
            }
        }

        @BindingPriority(20)
        public List<String> logObject(@Pipe Forwarder<List<String>,Object> objectPipe) {
            List<String> to = objectPipe.to(memoryDatabase);
            System.out.println("logObject");
            return to;
        }
    }


    /**
     * 调用超类方法
     * ConstructorStrategy(构造器策略)负责为任何给定的类创建一组预定义的构造器。除了上面的策略(复制动态类型的直接超类的每一个可见构造器)之外，
     * 还有三个其它的预定义策略：
     * 一个不创建任何构造器；
     * 另一个创建默认的构造器，
     * 该构造器会调用直接超类的默认构造器，如果没有这样的构造器，则会抛出异常；
     * 最后一种仅模仿超类的公共构造器
     *
     * 在Java类文件格式中，构造器通常与方法没什么区别，这样Byte Buddy允许将它们相同对待。但是，构造器需要包含调用另一个构造器的硬编码调用才能被Java运行时接受。由于这个原因，除了SuperMethodCall，
     * 大多数预定义实现应用于构造器时将无法创建有效的Java类, 然而可以使用自定义实现 ...
     * 通过 defineConstructor(定义构造器)方法定义你自己的构造器
     *
     * 对于类变基和重定义，构造器当然只是简单地保留，这使得ConstructorStrategy的规范过时了。相反，对于复制这些保留的构造器(和方法)的实现，需要指定一个ClassFileLocator(类文件定位器)，
     * 它允许查找包含了这些构造器定义的源类。Byte Buddy会尽最大努力识别源类文件的位置，例如，通过查询对应的ClassLoader或者通过查看应用的类路径。
     * 然而，当处理自定义的类加载器时，查看可能仍然会失败。然后，就要提供一个自定义ClassFileLocator
     */
    @Test
    public void callSuperMethodCall() {
        // 隐式继承Object的超类调用
        new ByteBuddy()
                .subclass(Object.class)
                .make();

        // 他等价于,也就是直接可以调用父类的构造器 ..
        new ByteBuddy()
                .subclass(Object.class, ConstructorStrategy.Default.IMITATE_SUPER_CLASS);
    }


    public interface First {
        default String qux() { return "FOO"; }
    }

    public interface Second {
        default String qux() { return "BAR"; }
    }
    /**
     * 调用默认方法, 需要在默认方法上 指定方法的接口 ... 否则如果方法签名一样,会产生歧义性 ...
     * Byte Buddy的DefaultMethodCall实现采用了优先接口列表。当拦截一个方法时，DefaultMethodCall将在第一个提到的接口上调用默认方法
     */
    @Test
    public void callDefaultMethodCall() throws InstantiationException, IllegalAccessException {
        System.out.println(((First) new ByteBuddy(ClassFileVersion.JAVA_V11)
                .subclass(Object.class)
                .implement(First.class)
                .implement(Second.class)
                // 第一个实现接口的方法优先
                .method(named("qux")).intercept(DefaultMethodCall.prioritize(First.class))
                // 找到歧义性方法报错 ...
//                .method(named("qux")).intercept(DefaultMethodCall.unambiguousOnly())
                .make()
                .load(ClassLoader.getSystemClassLoader())
                .getLoaded()
                .newInstance())
                .qux());
    }


    public static class UserType {
        public String doSomething() { return null; }
    }

    public interface Interceptor {
        String doSomethingElse();
    }

    public interface InterceptionAccessor {
        Interceptor getInterceptor();
        void setInterceptor(Interceptor interceptor);
    }

    public interface InstanceCreator {
        Object makeInstance();
    }

    /**
     * 访问字段
     * 通过FiledAccessor (字段访问器) 实现方法的读取或者写入
     *
     * 必须有 pojo的 setter  / getter ...
     * 通过FiledAccessor.ofBeanProperty() 创建这样的一个访问器(如果不想从方法名中获取字段名称) ...
     * FieldAcessor.ofFiled(String)显式定义字段名 .. 如果有需要在没有这个字段的情况下定义一个新的字段 ...
     * 当访问一个现有字段时通过 in 方法来指定定义字段的类型 ... java中 类继承结构中的多个类定义一个字段是合法的 ...
     * 这个过程中,一个类的一个字段能够被子类覆盖 ... 否则 byte Buddy 将遍历类结构访问它遇到的第一个字段 ...
     *
     * 假设我们想要为UserType 子类化(运行时),于是我们想为每一个接口表示的实例注册一个拦截器,
     * 根据前面的说法,我们可以将方法调用拦截到对应的字段,然后我们创建基于pojo形式的拦截器field即可,那么它的接口定义必须是
     * interface Interceptor {
     *   String doSomethingElse();
     * },
     * 对应的属性访问器是
     * interface InterceptionAccessor {
     *   Interceptor getInterceptor();
     *   void setInterceptor(Interceptor interceptor);
     * }
     *
     * 也就是我们的动态类型能够通过java bean 规范进行拦截器获取(通过setter / getter)
     * 然后我们就可以开始代理了 ...
     *
     * 最后我们为实例自定义拦截器 ... 让我们将HelloWorldInteceptor 拦截器应用到 新创建的实例上 ,注意它没有使用反射,而是通过字段访问器接口和工厂 ...
     *
     *
     * 目前除了讨论的实现之外,还包括
     * StubMethod  (返回存根,也就是返回类型的默认值,这样一个特定的方法调用可以被抑制,实现mock 模型, 引用类型的方法返回 null)
     *
     * ExceptionMethod 只抛出异常的方法, 可以从任何方法抛出已检查异常,即使这个方法没有声明这个异常 ..
     *
     * Forwarding 实现允许简单的调用转发到另一个与拦截方法的声明类型相同的方法, 当然 MethodDelegation 可以达到相同的效果,通过Forwarding 应用更简单的模型, 该模型可以覆盖不需要目标方法发现的用例 ...
     *
     * InvocationHandlerAdapter 允许使用Java的类库进行代理 ..
     *
     * InvokeDynamic 允许使用 引导方法(bootstrap)运行时动态绑定一个方法,这个方法可以从Java 7 开始访问 ...
     */
    @Test
    public void visitField() throws InstantiationException, IllegalAccessException, IOException {
        Class<? extends UserType> loaded = new ByteBuddy()
                .subclass(UserType.class)
                .method(not(isDeclaredBy(Object.class)))
                .intercept(MethodDelegation.toField("interceptor"))
                .defineField("interceptor", Interceptor.class, Visibility.PRIVATE)
                .implement(InterceptionAccessor.class)
                .intercept(FieldAccessor.ofBeanProperty())
                .make()
                .load(ClassLoader.getSystemClassLoader(), ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();

             // 于是使用新的动态userType,我们可以实现InstanceCreator 接口作为这个动态类型的工厂 ... 我们通过MethodDelegation 进行动态类型的默认构造器 调用 ..
        // 相当于 调用这个实例的方法,就相当于创建对象 ...
        // 注意我们需要使用动态用户类型的类加载器加载这个工厂,否则这个类型加载后对这个工厂不可见 ...
        InstanceCreator instanceCreator = new ByteBuddy()
                .subclass(InstanceCreator.class)
                .method(not(isDeclaredBy(Object.class)))
                .intercept(MethodDelegation.toConstructor(loaded))
                .make()
                // 如果两者加载是使用不同的类加载器,可能动态类对工厂不可见 ..
//                .load(ClassLoader.getSystemClassLoader())
                .load(loaded.getClassLoader())
                .getLoaded().newInstance();

        InterceptionAccessor o = (InterceptionAccessor) instanceCreator.makeInstance();
        System.out.println(o);

        class HelloWorldInterceptor implements Interceptor {
            @Override
            public String doSomethingElse() {
                return "Hello World!";
            }
        }

        o.setInterceptor(new HelloWorldInterceptor());
        UserType o1 = (UserType) o;
        System.out.println(o1.doSomething());

    }

    @Test
    public void proxyByInvokeHandler() throws InstantiationException, IllegalAccessException {

        String s = new String("百度");
        System.out.println(new ByteBuddy()
                .subclass(Object.class)
                .method(any())
                .intercept(InvocationHandlerAdapter.of(new InvocationHandler() {
                    @Override
                    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                        System.out.println("执行方法");
                        // pass
                        return method.invoke(s, args);
                    }
                }))
                .make()
                .load(ClassLoader.getSystemClassLoader())
                .getLoaded()
                .newInstance()
                .toString());
    }
}
