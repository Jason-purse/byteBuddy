package club.smileboy.app.tutorial;

import net.bytebuddy.implementation.bind.annotation.SuperCall;

import java.util.List;
import java.util.concurrent.Callable;

public class LoggerInterceptor {
    /**
     * @SuperCall 能够进行父类实现方法调用 ... 他引用的是父类方法实现 ...
     * @param zsuper super
     * @return
     * @throws Exception
     */
        public static List<String> log(@SuperCall Callable<List<String>> zsuper)  throws Exception{
            System.out.println("calling database");
            try {
                return zsuper.call();
            }finally {
                System.out.println("Returned from database");
            }
        }
    }