package club.smileboy.app.tutorial.innerPackage;
/**
 * @author FLJ
 * @date 2022/8/11
 * @time 16:55
 * @Description 对于包私有的类, jvm 虚拟机仅仅允许 同包访问
 *  于是,包私有的类需要通过相同的类加载加载 为了运行时期间必须等价(也就是不同包的类,包括动态类也就没办法 访问父类,更别提是一个类加载器加载的),那么 必然报错(这是jvm的限制)
 */
class Inner {
}
