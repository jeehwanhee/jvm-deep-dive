import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;

/**
 * 출처: 직접 작성한 실습 (3.3 신세대 에덴:생존자 = 8:1:1, 표시-복사 알고리즘)
 * VM 매개 변수: -XX:+UseSerialGC -Xms20m -Xmx20m -Xmn10m -XX:SurvivorRatio=8 -Xlog:gc*
 *
 * javac GenerationTest.java
 * java -XX:+UseSerialGC -Xms20m -Xmx20m -Xmn10m -XX:SurvivorRatio=8 -Xlog:gc* GenerationTest
 */
public class GenerationTest {

    private static final int _1MB = 1024 * 1024;

    // JIT가 쓰레기 배열 할당을 없애지 못하게 막는 용도
    static volatile byte[] sink;

    public static void main(String[] args) {
        System.out.println("JVM 옵션: " + ManagementFactory.getRuntimeMXBean().getInputArguments());
        System.out.println();

        printPools("시작 직후");

        // 계속 살아 있는 객체 (생존자 공간을 거쳐 구세대로 넘어가는지 관찰)
        byte[] keep = new byte[256 * 1024];

        long lastCount = totalGcCount();
        for (int i = 0; i < 60; i++) {
            sink = new byte[_1MB]; // 바로 쓰레기가 되는 1MB 배열
            long count = totalGcCount();
            if (count != lastCount) {
                lastCount = count;
                printPools("GC 누적 " + count + "회 직후 (반복 " + i + "번째)");
            }
        }

        System.out.println("유지한 객체 크기: " + (keep.length / 1024) + "KB");
        System.out.println();
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.println("수집기 " + gc.getName() + ": " + gc.getCollectionCount() + "회");
        }
    }

    private static long totalGcCount() {
        long sum = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            sum += gc.getCollectionCount();
        }
        return sum;
    }

    private static void printPools(String title) {
        System.out.println("[" + title + "]");
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            if (pool.getType() != MemoryType.HEAP) {
                continue;
            }
            System.out.printf("  %-18s 사용 %6dKB / 최대 %6dKB%n",
                    pool.getName(),
                    pool.getUsage().getUsed() / 1024,
                    pool.getUsage().getMax() / 1024);
        }
        System.out.println();
    }
}
