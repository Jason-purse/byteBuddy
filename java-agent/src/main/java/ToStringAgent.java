import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.FixedValue;
import net.bytebuddy.matcher.ElementMatchers;
import net.bytebuddy.utility.JavaModule;

import java.lang.instrument.Instrumentation;
import java.util.Optional;
/**
 * @author FLJ
 * @date 2022/8/10
 * @time 16:17
 * @Description  现在将拥有ToString 的类 都将对应的方法替换为返回transformed ...
 * 我们将学习到Byte Buddy的DynamicType.Builder,现在不需要担心 ..
 * 上述代码的结果是一个简单且无意义的应用程序,正确使用这个概念,  然而这能够轻易的面向切面编程(这是一个强大的工具) ..
 *
 * 注意它也可能指示被引导类加载器加载的类(在使用代理的时候) ...
 * 然而这需要某些准备工作,所有的第一步,引导类加载器呈现永远是 null(这确保它不可能通过反射在类上下文中加载一个类) ...
 * 然而有些时候需要加载一些帮助类到被检测类的类加载器中去支持 类的实现 ... 为了加载类到 引导类加载器中,Byte Buddy 能够创建
 * 一个jar文件并增加这些文件到引导类加载器的加载路径上,使它成为可能,然而它需要保存这些类到磁盘上 ... 这些类的文件夹将会通过使用 enableBootstrapInjection 命令指定(这会产生一个Instrumentation 接口去追加这些classes)
 * 注意到被检测的类所使用的所有用户类(同样需要放置在引导 查询路径上,这可能需要使用Instrumentation接口) ...
 */
public class ToStringAgent {
    public static void premain(String arguments, Instrumentation instrumentation) {
        new AgentBuilder.Default()
                .type(ElementMatchers.isAnnotatedWith(ToString.class))
                .transform(new AgentBuilder.Transformer() {
                    @Override
                    public DynamicType.Builder<?> transform(DynamicType.Builder<?> builder, TypeDescription typeDescription, ClassLoader classLoader, JavaModule javaModule) {
                        Optional<AnnotationDescription> optional = typeDescription.getDeclaredAnnotations().stream().filter(annotation -> ToString.class.isAssignableFrom(annotation.getClass())).findFirst();
                        if (optional.isPresent()) {
                            AnnotationDescription annotationDescription = optional.get();
                            AnnotationValue<?, ?> value = annotationDescription.getValue("value");
                            String resolveMethod = value.resolve(String.class);
                            if(resolveMethod.isBlank()) {
                                resolveMethod = "toString";
                            }
                            return builder.method(ElementMatchers.named(resolveMethod))
                                    .intercept(FixedValue.value("transformed"));
                        }
                        return builder;
                    }
                }).installOn(instrumentation);
    }

}
