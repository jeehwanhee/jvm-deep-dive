public class testGC {

    private static final int _1MB = 1024 * 1024;

    /**
     * 출처: 코드 3-7
     *
     * javac testGC.java
     * java -XX:+UseSerialGC -Xms20M -Xmx20M -Xmn10M -XX:SurvivorRatio=8 -Xlog:gc* testGC 1
     */
    public static void testAllocation() {
        byte[] alloc1, alloc2, alloc3, alloc4;
        // 2MB짜리 3개, 4MB짜리 1개 객체 생성
        alloc1 = new byte[2 * _1MB];
        alloc2 = new byte[2 * _1MB];
        alloc3 = new byte[2 * _1MB];
        alloc4 = new byte[4 * _1MB];
    }

    /**
     * 출처: 코드 3-8
     *
     * javac testGC.java
     * java -XX:+UseSerialGC -Xms20M -Xmx20M -Xmn10M -XX:SurvivorRatio=8 -Xlog:gc* -XX:PretenureSizeThreshold=3M testGC 2
     */
    public static void testPretenureSizeThreshold() {
        byte[] alloc;
        alloc = new byte[4 * _1MB];
    }

    /**
     * 출처: 코드 3-9
     *
     * javac testGC.java
     * java -XX:+UseSerialGC -Xms20M -Xmx20M -Xmn10M -XX:SurvivorRatio=8 -Xlog:gc* -Xlog:gc+age=trace -XX:MaxTenuringThreshold=1 testGC 3
     * java -XX:+UseSerialGC -Xms20M -Xmx20M -Xmn10M -XX:SurvivorRatio=8 -Xlog:gc* -Xlog:gc+age=trace -XX:MaxTenuringThreshold=15 -XX:TargetSurvivorRatio=80 testGC 3
     */
    public static void testTenuringThreshold() {
        byte[] alloc1, alloc2, alloc3;
        alloc1 = new byte[_1MB / 8];
        alloc2 = new byte[4 * _1MB];
        alloc3 = new byte[4 * _1MB]; // 첫 번째 GC 발생
        alloc3 = null;
        alloc2 = new byte[4 * _1MB]; // 두 번째 GC 발생
    }

    /**
     * 출처: 코드 3-10
     *
     * javac testGC.java
     * java -XX:+UseSerialGC -Xms20M -Xmx20M -Xmn10M -XX:SurvivorRatio=8 -Xlog:gc* -Xlog:gc+age=trace -XX:MaxTenuringThreshold=15 -XX:TargetSurvivorRatio=30 testGC 4
     */
    public static void testTenuringThreshold2() {
        byte[] alloc1, alloc_new, alloc2, alloc3;
        alloc1 = new byte[_1MB / 8];
        alloc_new = new byte[_1MB / 16];
        // alloc1 + alloc1 = 생존자 공간의 30% 초과
        alloc2 = new byte[4 * _1MB];
        alloc3 = new byte[4 * _1MB]; // 첫 번째 GC 발생
        alloc3 = null;
        alloc2 = new byte[4 * _1MB]; // 두 번째 GC 발생
    }

    public static void main(String[] args) {
        if (args.length == 0) {
            System.out.println("사용법: java [VM 매개 변수] AllocationMain <메서드 이름>");
            return;
        }

        switch (args[0]) {
            case "1" -> testAllocation();
            case "2" -> testPretenureSizeThreshold();
            case "3" -> testTenuringThreshold();
            case "4" -> testTenuringThreshold2();
            // 새 코드를 추가하면 여기에 case 를 한 줄 더 넣는다.
            default -> System.out.println("알 수 없는 메서드: " + args[0]);
        }
    }

    
}