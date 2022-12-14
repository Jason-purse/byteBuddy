# byteBuddy 字节码修改技术

## hello world
 @see ByteBuddyMain
## method proxy / custom method proxy
 @see MethodProxyTests
## field define and method insert

## 加载类的形式
1. 需要通过类加载器 加载动态类 ..
    不打破双亲委派机制的子类加载器(作为运行程序已存在的类加载器的子类加载器),这样通过新的类加载器加载动态类型
2. 正常情况,类加载器会查询父类加载器去尝试直接加载给定类型(通过名称) ,这意味着类加载器不可能加载类型,因为它的父类加载器可以感知加载类型(通过等价的名称感知类型),因此
    它创建了子类优先的类加载器尝试加载一个类型(通过自己查询,在父类查询之前),除此之外,这种方式类似于上面提到的方式，注意到这种方式没没有覆盖父类加载器的类型相反是遮盖这个其他类型 ...
3. 同样可以使用反射去注册一个类型到已经存在的类加载器中,通常来说,一个类加载器会通过给定的名称进行提供类型,使用反射,我们可以扭转这个原则，调用一个受保护的方法来将一个新类注入到类加载器中，而类加载器实际上并不知道如何定位这个动态类。

不幸的是,上面的方法都有它们自己的缺点:
- 如果我们创建了新的类加载器,这个类加载器会定义新的命名空间,这可能导致通过相同名称加载两个类(只要这些类通过两个不同的类加载器加载),这两个类将绝不会考虑是相等的(对于java 虚拟机来说),即使这两个类代表了一个等价的类实现  .. \
这个规则对于java 包来说也是等价的 .. 这意味着一个类"example.Foo"不能够访问另一个类"example.Bar"的包私有方法,如果两个类不是通过同一个类加载器加载的,这显然是不合理的,同样,如果"example.Bar"继承于 "example.Foo"并且覆盖了任何包私有的方法可能会变得 \
无法操作(但会委托给原来的实现) ..
- 无论什么时候加载类,它的类加载器将会查询任何类型(在这个类中所引用的类型,一旦引用其他类型的代码碎片被解析到),这个查询将会委托给相同的类加载器,想想一个场景,我们动态的创建了两个类"example.Foo"以及 "example.Bar",如果我们注入example.Foo 到一个存在的类加载器中,这个
类加载器将尝试获取exmaple.Bar,这个查询会失败,因为我们创建的是后者也是动态类并且对于类加载器来说是不可见的(因为前面的动态类也是反射注入的),因此反射方式不能够对于具有循环依赖的类来说是不可用的(类加载器期间生效的具有循环依赖关系的类),
因此大多数JVM 实现通过懒惰的解析引用类(在第一次激活使用的时候),这就是为什么类注入能够正常的工作而没有这些限制,此外，在实践中，由 Byte Buddy 创建的类通常不会受到这种循环的影响。

- 同样你可能觉得遇见循环依赖的机会少之又少,但是当你创建动态类型的时候,可能会触发叫做辅助性类型的创建,这些类型被ByteBuddy创建自动的提供对你创建的动态类型的访问,我们推荐你动态的加载创建的类(通过创建指定的 ClassLoader 而不是注册它们到一个存在的类加载器中,无论何时,应该尽可能这样) ...
- 在创建DyamicType.Unloaded 之后,这个类型能够通过使用一个ClassLoadingStrategy进行加载,如果没有这样的策略提供,ByteBuddy将会基于提供的类加载器推断一个策略并创建一个新的类加载器(仅当使用反射无法向引导类加载器注册时,否则这是默认行为),Byte Buddy提供了各种类加载策略(开箱即用) ..
### 类加载策略
这些策略定义在ClassLoadingStrategy.Default
- WRAPPER 策略将创建一个包装的类加载器
- CHILD_FIRST 策略将创建一个类似的类加载器(基于子类优先的语义)
- INJECTION 策略直接通过反射注入动态类型

WRAPPER 和 CHILD_FIRST 策略也可以用在所谓的清单版本(这里即使在加载类之后，类型的二进制格式也会被保留。),这些可替换的版本使得类加载器的类的二进制形式能够通过ClassLoader::getResourceAsStream方法访问 ..  \
然而这些需要类加载器维护一个类的完全二进制呈现的引用(这将消费JVM 的堆空间),因此你应该仅仅实在是需要访问二进制形式的时候才使用这个清单版本 ,因为INJECTION 策略通过反射工作并且没有可能性去改变 ClassLoader::getResourceAsStream \
的方法,它本质上是不可能使用在清单版本中 ...