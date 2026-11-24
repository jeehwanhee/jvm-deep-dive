/**
 * 출처: 코드 4-5
 * VM 매개 변수: 없음 (책에 따로 적혀 있지 않음)
 *
 * javac DeadLockMonitoringTest.java
 * java DeadLockMonitoringTest
 */
public class DeadLockMonitoringTest {
    static class SynAddRunnalbe implements Runnable {
        int a, b;

        public SynAddRunnalbe(int a, int b) {
            this.a = a;
            this.b = b;
        }

        @Override
        public void run() {
            synchronized (Integer.valueOf(a)) {         // ❶
                synchronized (Integer.valueOf(b)) {     // ❶
                    System.out.println(a + b);
                }
            }
        }
    }

    public static void main(String[] args) {
        for (int i = 0; i < 100; i++) {  // 총 200개의 스레드 생성
            // 1+2를 수행하는 스레드
            new Thread(new SynAddRunnalbe(1, 2)).start();
            // 2+1을 수행하는 스레드
            new Thread(new SynAddRunnalbe(2, 1)).start();
        }
    }
}