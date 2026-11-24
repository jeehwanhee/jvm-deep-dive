/**
 * 출처: 코드 4-2
 * VM 매개 변수: -Xmx10m -XX:+UseSerialGC -XX:-UseCompressedOops
 *
 * javac JHSDBTestCase.java
 * java -Xmx10m -XX:+UseSerialGC -XX:-UseCompressedOops JHSDBTestCase
 */
public class JHSDBTestCase {

    static class Test {
        static ObjectHolder staticObj = new ObjectHolder();
        ObjectHolder instanceObj = new ObjectHolder();

        void foo() throws InterruptedException {
            ObjectHolder localObj = new ObjectHolder();
            System.out.println("done");    // ❶ 중단점 설정
            Thread.sleep(Long.MAX_VALUE);  // JHSDB를 붙일 수 있게 멈춰 둠 (책에는 없음)
        }
    }

    private static class ObjectHolder {}

    public static void main(String[] args) throws InterruptedException {
        Test test = new JHSDBTestCase.Test();
        test.foo();
    }
}