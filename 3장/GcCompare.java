import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Random;
import java.util.Scanner;

/**
 * 출처: 직접 작성한 실습 (3.5 컬렉터 비교: 처리량과 지연 시간)
 * VM 매개 변수: 없음 (컬렉터 옵션과 -Xms512m -Xmx512m 은 이 프로그램이 자식 JVM에 자동으로 붙임)
 *
 * javac GcCompare.java
 * java GcCompare
 *
 * 특정 컬렉터 하나만 보고 싶을 때:
 * java -XX:+UseG1GC -Xms512m -Xmx512m GcCompare worker
 *
 * ================= 이 프로그램이 하는 일 =================
 * 1) 그냥 실행하면(= driver 모드) 비교해보고 싶은 GC 컬렉터(Serial/Parallel/G1/ZGC/Shenandoah)
 *    각각에 대해 자식 JVM을 하나씩 새로 띄워서 똑같은 작업을 시키고, 결과를 모아 표로 보여준다.
 * 2) 자식 JVM(= worker 모드)은 "서버가 요청을 계속 처리하는 상황"을 흉내 낸다.
 *    - 오래 사는 캐시 데이터를 먼저 채워두고,
 *    - 그 위에서 금방 버려지는 작은 객체들을 계속 만들었다가 버리는 일을 반복하면서,
 *    - 요청 하나를 처리하는 데 걸린 시간(지연 시간)을 매번 측정한다.
 * 3) worker는 측정이 끝나면 "RESULT,..." 로 시작하는 한 줄을 출력하고, driver는 그 줄만 읽어서 표로 정리한다.
 *
 * ================= 결과 표에 나오는 값들의 의미 =================
 * - 컬렉터       : 테스트한 GC(Garbage Collector) 종류 이름
 * - 총시간(ms)   : 측정 구간(MEASURE_OPS번의 요청)을 처리하는 데 걸린 전체 시간 (밀리초)
 * - 처리량(op/s) : 1초에 몇 번의 "요청"을 처리할 수 있었는지 (클수록 좋음)
 * - 평균(us)     : 요청 하나를 처리하는 데 걸린 시간의 평균 (마이크로초, 1us = 1/1,000,000초)
 * - p99(us)      : 가장 느렸던 1%를 제외한 나머지, 즉 "요청의 99%가 이 시간 안에 끝났다"는 값
 * - p99.9(us)    : 위와 같은 개념이지만 더 극단적인 경우(가장 느린 0.1%)까지 포함한 값
 * - 최대(us)     : 측정 구간에서 가장 오래 걸린 요청 하나의 시간 (GC로 인한 '멈춤'이 여기 가장 크게 드러남)
 * - GC횟수       : 측정이 끝날 때까지 일어난 GC(수집) 총 횟수
 * (평균은 작은데 p99나 최대가 크게 튀는 컬렉터는 "평소엔 빠른데 가끔 크게 멈춘다"는 뜻이다)
 *
 * ================= 작업 부하(워크로드) 설정값 의미 =================
 * - CACHE_SLOTS       : 오래 사는 캐시 칸의 개수. 이 칸들은 시간이 지나면 구세대(오래된 객체 영역)에 자리 잡는다.
 * - CACHE_ENTRY_BYTES : 캐시 한 칸의 크기. CACHE_SLOTS * CACHE_ENTRY_BYTES = 캐시 전체 크기(약 128MB).
 * - WARMUP_OPS        : 측정을 시작하기 전에 미리 처리해보는 "요청" 횟수(시간은 재지 않음).
 *                       JIT 컴파일 등으로 초반이 유독 느린 구간을 측정에서 빼기 위한 것이다.
 * - MEASURE_OPS       : 실제로 시간을 측정하는 "요청" 처리 횟수. 표의 모든 값은 이 구간만으로 계산한다.
 * - HEAP / HEAP_MAX   : 자식 JVM에 똑같이 줄 최소/최대 힙 크기(-Xms512m / -Xmx512m).
 *                       모든 컬렉터에 같은 힙 크기를 줘야 공정하게 비교할 수 있다.
 */
public class GcCompare {

    // 비교할 컬렉터 이름과, 그 컬렉터를 켜는 JVM 옵션을 한 쌍씩 적어둔다.
    private static final String[][] COLLECTORS = {
            {"Serial", "-XX:+UseSerialGC"},
            {"Parallel", "-XX:+UseParallelGC"},
            {"G1", "-XX:+UseG1GC"},
            {"ZGC", "-XX:+UseZGC"},
            {"Shenandoah", "-XX:+UseShenandoahGC"},
    };

    private static final String HEAP = "-Xms512m";
    private static final String HEAP_MAX = "-Xmx512m";

    // 작업 부하(워크로드) 설정 - 각 값의 의미는 파일 맨 위 주석을 참고
    private static final int CACHE_SLOTS = 32 * 1024;
    private static final int CACHE_ENTRY_BYTES = 4 * 1024;
    private static final int WARMUP_OPS = 200_000;
    private static final int MEASURE_OPS = 1_500_000;

    // 시간 단위 변환 상수 (System.nanoTime()은 항상 나노초 단위를 반환한다)
    private static final long NANOS_PER_MICRO = 1_000L;
    private static final long NANOS_PER_MILLI = 1_000_000L;
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("worker")) {
            worker();
        } else {
            driver();
        }
    }

    // ========== 구동 쪽: 컬렉터마다 자식 JVM을 띄워서 결과를 모아 표로 출력 ==========

    private static void driver() throws Exception {
        // System.getProperty("java.class.path") : 지금 이 프로그램을 실행할 때 쓴 클래스패스(-cp 값)를
        // 그대로 문자열로 돌려준다. 자식 JVM도 같은 클래스(GcCompare)를 찾아 실행해야 하므로,
        // 이 값을 그대로 자식에게 "-cp" 옵션으로 넘겨줄 것이다.
        String classPath = System.getProperty("java.class.path");

        // System.getProperty("java.version") : 현재 실행 중인 JDK의 버전 문자열(예: "25.0.5")을 반환한다.
        // Runtime.getRuntime().availableProcessors() : 이 컴퓨터에서 JVM이 쓸 수 있는 CPU 코어 수를 반환한다.
        System.out.println("JDK: " + System.getProperty("java.version")
                + " / 프로세서: " + Runtime.getRuntime().availableProcessors() + "개"
                + " / 힙: 512MB 고정 / 측정 요청 수: " + MEASURE_OPS);
        System.out.println("컬렉터마다 자식 JVM을 따로 실행합니다. 몇 십 초 걸릴 수 있습니다.");
        System.out.println();

        printRow("컬렉터", "총시간(ms)", "처리량(op/s)", "평균(us)", "p99(us)", "p99.9(us)", "최대(us)", "GC횟수");

        for (String[] collector : COLLECTORS) {
            String collectorName = collector[0];
            String collectorOption = collector[1];

            // 이 컬렉터로 자식 JVM을 하나 띄울 때 쓸 명령어를 그대로 나열한다.
            // 예: java -XX:+UseG1GC -Xms512m -Xmx512m -cp <classpath> GcCompare worker
            String[] command = {
                    "java", collectorOption, HEAP, HEAP_MAX,
                    "-cp", classPath, "GcCompare", "worker"
            };

            String resultLine = runWorkerAndGetResult(command);
            if (resultLine == null) {
                System.out.println(collectorName + " 실행 실패 (이 JDK에서 지원하지 않는 컬렉터일 수 있음)");
                continue;
            }

            // worker가 출력한 한 줄: "RESULT,총시간,처리량,평균,p99,p99.9,최대,GC횟수"
            String[] fields = resultLine.split(",");
            printRow(collectorName, fields[1], fields[2], fields[3], fields[4], fields[5], fields[6], fields[7]);
        }

        System.out.println();
        System.out.println("읽는 법: 처리량은 클수록, 지연(평균·p99·p99.9·최대)은 작을수록 좋습니다.");
    }

    // 자식 JVM을 실행해서 "RESULT,..." 로 시작하는 줄을 찾아 반환한다. 실패하면 null을 반환한다.
    //
    // 여기서 쓰는 java.lang.ProcessBuilder / java.lang.Process는 "다른 프로그램을 실행시키는" API다.
    // 터미널에서 `java ... GcCompare worker` 명령을 직접 치는 것과 같은 일을, 코드로 대신 해주는 것이라고
    // 생각하면 된다. 이 메서드가 끝나면 자식 JVM 하나가 뜨고, 측정을 마치고, 종료된다.
    private static String runWorkerAndGetResult(String[] command) throws Exception {
        // ProcessBuilder(command) : command 배열(["java", "-XX:...", ..., "worker"])을
        // 터미널에 입력할 명령어처럼 그대로 받아서, 실행을 "준비"만 해두는 객체.
        ProcessBuilder processBuilder = new ProcessBuilder(command);

        // 자식 JVM이 표준에러(System.err)로 뭔가를 찍더라도 화면에 보여주지 않고 그냥 버린다.
        // (Redirect.DISCARD = "어디로도 보내지 말고 버려라"는 뜻의 미리 정의된 값)
        processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);

        // start() : 이제 실제로 자식 프로세스(JVM)를 하나 띄운다. 돌아오는 Process 객체로
        // 그 자식 프로세스의 출력을 읽거나, 종료를 기다리거나, 강제로 죽일 수 있다.
        Process process = processBuilder.start();

        String resultLine = null;
        // process.getInputStream() : 자식 프로세스가 println으로 찍은 "표준출력" 내용을 읽는 통로.
        // new Scanner(...) : 그 통로를 한 줄씩(hasNextLine/nextLine) 쉽게 읽게 도와주는 보조 도구.
        // (Scanner(System.in)으로 콘솔 입력을 받던 것과 같은 클래스이고, 대상만 "자식 프로세스의 출력"으로 바뀐 것)
        try (Scanner workerOutput = new Scanner(process.getInputStream())) {
            while (workerOutput.hasNextLine()) {
                String line = workerOutput.nextLine();
                // 자식(worker)이 여러 줄을 찍을 수도 있으니, "RESULT,"로 시작하는 줄만 기억해둔다.
                if (line.startsWith("RESULT,")) {
                    resultLine = line;
                }
            }
        }

        // waitFor() : 자식 프로세스가 완전히 끝날 때까지 여기서 기다렸다가, 그 종료 코드를 돌려준다.
        // 정상 종료라면 0, 뭔가 잘못됐으면 보통 0이 아닌 값이다.
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            return null;
        }
        return resultLine;
    }

    // ========== 표 출력 ==========

    // 칸마다 몇 글자 너비로 맞출지
    private static final int[] COLUMN_WIDTHS = {12, 11, 14, 9, 9, 11, 10, 8};

    // 첫 칸(컬렉터 이름)은 왼쪽 정렬, 나머지 숫자 칸은 오른쪽 정렬로 한 줄을 출력한다.
    //
    // 매개변수 타입이 "String... columns"인 건 가변 인자(varargs)라는 문법이다.
    // printRow("a", "b")처럼 호출해도 되고 printRow("a","b","c",...)처럼 몇 개를 넘기든 다 받아주며,
    // 메서드 안에서는 그냥 String[] columns 배열처럼 쓸 수 있다.
    private static void printRow(String... columns) {
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < columns.length; i++) {
            if (i == 0) {
                line.append(padRight(columns[i], COLUMN_WIDTHS[i]));
            } else {
                line.append(padLeft(columns[i], COLUMN_WIDTHS[i]));
            }
            line.append(' ');
        }
        System.out.println(line.toString());
    }

    // 한글은 터미널에서 영문/숫자보다 2배 넓게 보인다. 그래서 "글자 수"가 아니라
    // "화면에 보이는 너비"로 패딩을 계산해야 헤더(한글)와 데이터(숫자/영문) 줄이 맞는다.
    //
    // text.toCharArray() : 문자열을 글자(char) 하나하나의 배열로 쪼갠다. char 하나는 유니코드
    // 문자 하나를 숫자(코드)로 담고 있는데, 그 숫자 범위를 보면 "이게 한글인지 아닌지" 알 수 있다.
    // 0xAC00, 0xD7A3 같은 건 16진수 숫자 표기일 뿐이고, 유니코드 표에서 "한글 음절(가~힣)"이
    // 모여 있는 범위를 뜻한다(0x3130~0x318F는 "ㄱ", "ㅏ"처럼 자음/모음만 있는 한글 범위).
    private static int visibleWidth(String text) {
        int width = 0;
        for (char ch : text.toCharArray()) {
            boolean isKorean = (ch >= 0xAC00 && ch <= 0xD7A3)   // 완성된 한글 글자 (가~힣)
                    || (ch >= 0x3130 && ch <= 0x318F);          // 자음/모음만 있는 한글
            width += isKorean ? 2 : 1;
        }
        return width;
    }

    // StringBuilder : 문자열을 "+"로 계속 이어 붙이면 그때마다 새 문자열이 만들어져 느려질 수 있는데,
    // StringBuilder는 내부에 늘어나는 버퍼를 하나 두고 거기에 이어 붙이기만 해서 더 효율적이다.
    // 다 이어 붙인 뒤 toString()으로 최종 String을 꺼낸다.
    private static String padRight(String text, int width) {
        StringBuilder sb = new StringBuilder(text);
        while (visibleWidth(sb.toString()) < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    private static String padLeft(String text, int width) {
        StringBuilder sb = new StringBuilder();
        while (sb.length() + visibleWidth(text) < width) {
            sb.append(' ');
        }
        sb.append(text);
        return sb.toString();
    }

    // ========== 작업 쪽: 서버처럼 요청을 처리하는 척하면서 지연 시간을 잰다 ==========

    private static void worker() {
        Random random = new Random(42); // 매번 같은 순서로 재현되도록 시드를 고정

        // 오래 사는 데이터(캐시) 역할. 시간이 지나면 구세대(오래된 객체 영역)에 자리 잡는다.
        byte[][] cache = new byte[CACHE_SLOTS][];
        for (int i = 0; i < CACHE_SLOTS; i++) {
            cache[i] = new byte[CACHE_ENTRY_BYTES];
        }

        long[] latencies = new long[MEASURE_OPS]; // 요청 하나하나가 걸린 시간을 모두 기록
        long sink = 0; // printResult()의 주석 참고 - JIT 최적화를 막기 위한 용도

        long measureStartTime = 0;
        for (int op = -WARMUP_OPS; op < MEASURE_OPS; op++) {
            // op가 0이 되는 순간부터 "진짜 측정 구간"이 시작된다. 그 전(음수 구간)은 워밍업이라 시간을 재지 않는다.
            if (op == 0) {
                measureStartTime = System.nanoTime();
            }

            long requestStart = System.nanoTime();
            sink += simulateOneRequest(cache, random);
            long requestEnd = System.nanoTime();

            if (op >= 0) {
                latencies[op] = requestEnd - requestStart;
            }
        }
        long totalMeasureNanos = System.nanoTime() - measureStartTime;

        printResult(latencies, totalMeasureNanos, sink);
    }

    // 요청 하나를 처리하는 척한다: 금방 버려질 작은 객체 여러 개를 만들고,
    // 가끔 그 결과를 캐시의 랜덤한 칸에 새로 저장해서 오래 사는 객체도 계속 교체되게 한다.
    // 반환값은 "방금 할당한 바이트 수"일 뿐이며, 바깥에서 sink에 더해 흘려보내는 용도다.
    private static long simulateOneRequest(byte[][] cache, Random random) {
        long bytesAllocated = 0;

        byte[][] temporaryData = new byte[8][];
        for (int i = 0; i < temporaryData.length; i++) {
            temporaryData[i] = new byte[512 + random.nextInt(512)];
            bytesAllocated += temporaryData[i].length;
        }

        // 10번 중 1번 꼴로 캐시의 랜덤한 칸을 새 값으로 바꿔친다 (오래 사는 객체가 계속 교체됨).
        if (random.nextInt(10) == 0) {
            cache[random.nextInt(cache.length)] = new byte[CACHE_ENTRY_BYTES];
        }

        return bytesAllocated;
    }

    // 측정 결과를 계산해서 "RESULT,..." 한 줄로 출력한다.
    private static void printResult(long[] latencies, long totalMeasureNanos, long sink) {
        Arrays.sort(latencies); // 정렬해야 p99, p99.9 같은 백분위 값을 뽑을 수 있다

        long sumNanos = 0;
        for (long latency : latencies) {
            sumNanos += latency;
        }

        // GarbageCollectorMXBean : JVM 내부의 "GC 담당자"를 자바 코드에서 들여다볼 수 있게 해주는 객체.
        // ManagementFactory.getGarbageCollectorMXBeans()는 지금 이 JVM에서 실제로 동작 중인
        // GC 담당자 목록을 돌려준다(보통 "젊은 세대 담당"과 "구세대/전체 담당"이 각각 하나씩 있다).
        // getCollectionCount()는 그 담당자가 지금까지 GC를 몇 번 실행했는지를 알려준다.
        long gcCount = 0;
        for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcCount += gcBean.getCollectionCount();
        }

        long totalMs = totalMeasureNanos / NANOS_PER_MILLI;
        long opsPerSec = MEASURE_OPS * NANOS_PER_SECOND / totalMeasureNanos;
        long avgUs = sumNanos / MEASURE_OPS / NANOS_PER_MICRO;

        // latencies는 이미 Arrays.sort()로 "가장 빠른 것 -> 가장 느린 것" 순으로 정렬돼 있다.
        // 그러니 "앞에서부터 99%번째 자리"에 있는 값이 곧 p99 값이다.
        // (int)는 "소수점 있는 계산 결과를 배열 인덱스로 쓰려면 정수로 바꿔야 한다"는 형변환이다.
        // 예: MEASURE_OPS가 백만이면 0.99를 곱한 99만 번째(인덱스) 값을 꺼내는 것.
        long p99Us = latencies[(int) (MEASURE_OPS * 0.99)] / NANOS_PER_MICRO;
        long p999Us = latencies[(int) (MEASURE_OPS * 0.999)] / NANOS_PER_MICRO;
        long maxUs = latencies[MEASURE_OPS - 1] / NANOS_PER_MICRO; // 정렬했으니 맨 마지막 칸이 가장 큰 값(= 최댓값)

        // sink 값을 실제로 "사용"하는 척해서, JIT 컴파일러가 "아무도 안 쓰는 값이니 할당 자체를 지워버리자"고
        // 최적화하는 것을 막는다. sink가 정확히 42가 될 일은 거의 없으므로 이 줄은 거의 실행되지 않는다.
        if (sink == 42) {
            System.out.println();
        }

        System.out.println("RESULT," + totalMs + "," + opsPerSec + "," + avgUs + ","
                + p99Us + "," + p999Us + "," + maxUs + "," + gcCount);
    }
}
