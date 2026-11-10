import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;

/**
 * 출처: 직접 작성한 실습 (3.3 신세대 에덴:생존자 = 8:1:1, 표시-복사 알고리즘)
 * VM 매개 변수: -XX:+UseSerialGC -Xms20m -Xmx20m -Xmn10m -XX:SurvivorRatio=8 -Xlog:gc
 *
 * javac GenerationTest.java
 * java -XX:+UseSerialGC -Xms20m -Xmx20m -Xmn10m -XX:SurvivorRatio=8 -Xlog:gc GenerationTest
 *
 * -Xlog:gc* 대신 -Xlog:gc를 쓰면 GC 한 번마다 요약 한 줄만 찍힌다.
 * (예: "GC(0) Pause Young (Allocation Failure) 9M->2M(19M) 2.010ms")
 * -Xlog:gc*는 "gc로 시작하는 모든 하위 태그"를 전부 켜는 옵션이라
 * Full GC의 단계별 분해(Phase 1~4), Eden/From/To 영역별 상세, 메타스페이스, CPU 사용량 같은
 * 세부 로그까지 다 쏟아져서 줄 수가 훨씬 많아진다. 세부 로그가 보고 싶을 때만 -Xlog:gc*로 바꿔서 실행하자.
 *
 * ================= 이 실습이 보여주는 것 =================
 * - 힙은 "에덴(Eden)", "생존자(Survivor) 0/1", "구세대(Tenured/Old)" 영역으로 나뉜다.
 * - 새로 만든 객체는 항상 에덴에서 태어난다.
 * - 에덴이 가득 차면 Minor GC(젊은 세대 GC)가 일어나고, 그때 살아남은 객체만 생존자 영역으로 옮겨진다.
 * - 생존자 영역에서 여러 번 살아남은 객체는 결국 구세대로 옮겨진다(이를 "승격"이라 부른다).
 * - 이 코드는 각 영역의 사용량을 주기적으로 출력해서, 위 과정이 실제로 일어나는 모습을 눈으로 보여준다.
 */
public class GenerationTest {

    private static final int ONE_MB = 1024 * 1024;
    private static final int LOOP_COUNT = 60;

    // GC로 회수될 쓰레기 객체를 담아두는 변수.
    // JIT 컴파일러는 "아무도 읽지 않는 값에 대한 할당은 생략해도 된다"고 최적화해버릴 수 있다.
    // sink를 static volatile로 선언해서 "다른 곳에서 언제든 읽을 수 있는 값"으로 취급하게 만들면,
    // 할당 자체가 사라지는 최적화를 막을 수 있다.
    static volatile byte[] sink;

    public static void main(String[] args) {
        System.out.println("JVM 옵션: " + ManagementFactory.getRuntimeMXBean().getInputArguments());
        System.out.println();

        printHeapUsage("시작 직후");

        // 프로그램이 끝날 때까지 계속 살아있는 객체.
        // 이 객체가 생존자 영역을 거쳐 구세대로 승격되는지 관찰하는 것이 목적이다.
        byte[] keepAlive = new byte[256 * 1024];

        long gcCountBefore = getTotalGcCount();

        for (int i = 0; i < LOOP_COUNT; i++) {
            // 매번 1MB짜리 배열을 새로 만들고 바로 버린다 (참조를 유지하지 않으니 금방 쓰레기가 됨).
            // 이렇게 에덴을 빠르게 채워서 Minor GC가 여러 번 일어나게 만든다.
            sink = new byte[ONE_MB];

            long gcCountNow = getTotalGcCount();
            if (gcCountNow != gcCountBefore) {
                gcCountBefore = gcCountNow;
                printHeapUsage("GC 누적 " + gcCountNow + "회 직후 (반복 " + i + "번째)");
            }
        }

        System.out.println("유지한 객체 크기: " + (keepAlive.length / 1024) + "KB");
        System.out.println();

        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            System.out.println("수집기 " + gcBean.getName() + ": " + gcBean.getCollectionCount() + "회");
        }
    }

    // 지금까지 일어난 GC 횟수를 모두 더해서 반환한다.
    // (JVM에는 보통 "젊은 세대 전담 수집기"와 "전체(Full) GC 담당 수집기"가 따로 있고,
    //  둘 다 GarbageCollectorMXBean으로 조회할 수 있다)
    private static long getTotalGcCount() {
        long total = 0;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            total += gcBean.getCollectionCount();
        }
        return total;
    }

    // 힙을 이루는 각 영역(에덴, 생존자 0/1, 구세대 등)의 사용량을 출력한다.
    private static void printHeapUsage(String title) {
        System.out.println("[" + title + "]");
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            // 힙이 아닌 영역(메서드 영역 등)은 관심 대상이 아니므로 건너뛴다.
            if (pool.getType() != MemoryType.HEAP) {
                continue;
            }
            long usedKb = pool.getUsage().getUsed() / 1024;
            long maxKb = pool.getUsage().getMax() / 1024;
            System.out.println("  " + padRight(pool.getName(), 18)
                    + " 사용 " + padLeft(usedKb + "KB", 8)
                    + " / 최대 " + padLeft(maxKb + "KB", 8));
        }
        System.out.println();
    }

    // 문자열 오른쪽에 공백을 채워서 길이를 맞춘다 (왼쪽 정렬처럼 보이게)
    private static String padRight(String text, int width) {
        StringBuilder sb = new StringBuilder(text);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    // 문자열 왼쪽에 공백을 채워서 길이를 맞춘다 (오른쪽 정렬처럼 보이게)
    private static String padLeft(String text, int width) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() < width - text.length()) {
            sb.append(' ');
        }
        sb.append(text);
        return sb.toString();
    }
}
