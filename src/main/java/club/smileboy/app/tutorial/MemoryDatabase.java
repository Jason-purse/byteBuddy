package club.smileboy.app.tutorial;

import java.util.Arrays;
import java.util.List;
/**
 * @author FLJ
 * @date 2022/8/11
 * @time 16:42
 * @Description 基础类
 */
public class MemoryDatabase {
    public List<String> load(String info) {
        return Arrays.asList(info + ": foo", info + ": bar");
    }
}