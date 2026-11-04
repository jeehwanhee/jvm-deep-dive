import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * 출처: 직접 작성한 실습 (3.2 강한·부드러운·약한·유령 참조)
 * VM 매개 변수: -Xms20m -Xmx20m
 *
 * javac ReferenceTypesTest.java
 * java -Xms20m -Xmx20m ReferenceTypesTest
 */
public class ReferenceTypesTest {

    private static final int _1MB = 1024 * 1024;

    public static void main(String[] args) throws Exception {
        strongAndWeak();
        soft();
        phantom();
    }

    // 1. 강한 참조가 있으면 약한 참조 대상도 살아 있고, 강한 참조를 끊으면 다음 GC에서 사라진다
    private static void strongAndWeak() {
        System.out.println("===== 1. 강한 참조 + 약한 참조 =====");

        byte[] strong = new byte[_1MB];
        WeakReference<byte[]> weak = new WeakReference<>(strong);

        System.out.println("GC 전: weak.get() != null -> " + (weak.get() != null));

        System.gc();
        System.out.println("GC 후 (강한 참조 유지, 길이 " + strong.length + "): weak.get() != null -> "
                + (weak.get() != null));

        strong = null; // 강한 참조 제거
        System.gc();
        System.out.println("GC 후 (강한 참조 제거): weak.get() != null -> " + (weak.get() != null));
        System.out.println();
    }

    // 2. 부드러운 참조: 일반 GC에서는 살아남고, 메모리가 부족해지면 OOM 직전에 회수된다
    private static void soft() {
        System.out.println("===== 2. 부드러운 참조 =====");

        SoftReference<byte[]> soft = new SoftReference<>(new byte[_1MB]);

        System.gc();
        System.out.println("System.gc() 후: soft.get() != null -> " + (soft.get() != null));

        List<byte[]> hold = new ArrayList<>();
        try {
            while (soft.get() != null) {
                hold.add(new byte[256 * 1024]); // 강한 참조로 계속 쌓아서 메모리를 압박
            }
            System.out.println("메모리 압박 중 회수됨: 256KB 블록 " + hold.size() + "개("
                    + (hold.size() / 4) + "MB) 할당 시점에 soft.get() == null");
        } catch (OutOfMemoryError e) {
            System.out.println("OutOfMemoryError 발생 (soft.get() != null -> " + (soft.get() != null) + ")");
        } finally {
            hold.clear();
        }
        System.out.println();
    }

    // 3. 유령 참조: get()은 항상 null이고, 대상이 회수될 때 큐로 알림만 받는다
    private static void phantom() throws InterruptedException {
        System.out.println("===== 3. 유령 참조 =====");

        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        PhantomReference<Object> phantom = new PhantomReference<>(new Object(), queue);

        System.out.println("GC 전: phantom.get() -> " + phantom.get() + " (항상 null)");

        System.gc();
        Reference<?> notified = queue.remove(2000); // 최대 2초 대기
        if (notified == null) {
            System.out.println("2초 안에 알림을 받지 못함");
        } else {
            System.out.println("GC 후: 회수 알림 수신 -> 큐에서 꺼낸 참조가 phantom과 같은가? " + (notified == phantom));
        }
    }
}
