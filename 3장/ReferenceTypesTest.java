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
 *
 * ================= 참조 종류 정리 =================
 * - 강한 참조(Strong Reference) : 우리가 평소에 쓰는 보통의 참조(byte[] a = ...). GC가 절대 회수하지 않는다.
 * - 부드러운 참조(Soft Reference): 메모리가 넉넉하면 그대로 살려두지만, 메모리가 부족해지면
 *                                  OOM이 나기 직전에 GC가 회수해도 된다고 여기는 참조.
 * - 약한 참조(Weak Reference)   : 강한 참조가 하나도 남아있지 않으면 다음 GC 때 바로 회수될 수 있는 참조.
 * - 유령 참조(Phantom Reference): get()이 항상 null을 반환한다. 대상 객체 자체를 다시 꺼내 쓸 수는 없고,
 *                                  "이 객체가 회수되는 시점"만 ReferenceQueue로 알림받을 때 쓴다.
 */
public class ReferenceTypesTest {

    private static final int ONE_MB = 1024 * 1024;

    public static void main(String[] args) throws Exception {
        strongAndWeakReference();
        softReference();
        phantomReference();
    }

    // 1. 강한 참조가 남아있는 동안은 약한 참조가 가리키는 객체도 함께 살아 있고,
    //    강한 참조를 끊으면(null로 만들면) 다음 GC에서 바로 사라진다.
    private static void strongAndWeakReference() {
        System.out.println("===== 1. 강한 참조 + 약한 참조 =====");

        byte[] strongRef = new byte[ONE_MB];
        WeakReference<byte[]> weakRef = new WeakReference<>(strongRef);

        boolean aliveBeforeGc = weakRef.get() != null;
        System.out.println("GC 전: 약한 참조 대상이 살아있는가 -> " + aliveBeforeGc);

        System.gc(); // GC를 바로 실행해달라고 "요청"한다 (강제는 아니지만 대부분의 JVM에서 바로 실행된다)
        boolean aliveWithStrongRef = weakRef.get() != null;
        System.out.println("GC 후 (강한 참조 유지, 길이 " + strongRef.length
                + "): 약한 참조 대상이 살아있는가 -> " + aliveWithStrongRef);

        strongRef = null; // 강한 참조 제거 -> 이제 이 객체를 가리키는 건 weakRef뿐이다
        System.gc();
        boolean aliveAfterStrongRefRemoved = weakRef.get() != null;
        System.out.println("GC 후 (강한 참조 제거): 약한 참조 대상이 살아있는가 -> " + aliveAfterStrongRefRemoved);
        System.out.println();
    }

    // 2. 부드러운 참조: 평소에는 살아남지만, 메모리가 부족해지면 OOM이 나기 전에 GC가 회수해준다.
    private static void softReference() {
        System.out.println("===== 2. 부드러운 참조 =====");

        SoftReference<byte[]> softRef = new SoftReference<>(new byte[ONE_MB]);

        System.gc();
        System.out.println("System.gc() 후: 부드러운 참조 대상이 살아있는가 -> " + (softRef.get() != null));

        // 강한 참조로 메모리를 계속 쌓아서 "메모리가 부족한 상황"을 일부러 만들어본다.
        List<byte[]> memoryPressure = new ArrayList<>();
        try {
            while (softRef.get() != null) {
                memoryPressure.add(new byte[256 * 1024]); // 256KB씩 계속 할당해서 메모리를 압박
            }
            int blockCount = memoryPressure.size();
            System.out.println("메모리 압박 중 회수됨: 256KB 블록 " + blockCount + "개("
                    + (blockCount / 4) + "MB) 할당 시점에 부드러운 참조가 회수됨");
        } catch (OutOfMemoryError e) {
            // 환경에 따라 부드러운 참조가 회수되기도 전에 OOM이 먼저 날 수도 있다.
            System.out.println("OutOfMemoryError 발생 (그 순간에도 부드러운 참조가 살아있었는가 -> "
                    + (softRef.get() != null) + ")");
        } finally {
            memoryPressure.clear(); // 메모리 압박용으로 쌓아둔 객체를 모두 비워서 정리
        }
        System.out.println();
    }

    // 3. 유령 참조: get()은 항상 null이고, 대상이 "회수될 때" 큐로 알림만 받는다.
    private static void phantomReference() throws InterruptedException {
        System.out.println("===== 3. 유령 참조 =====");

        ReferenceQueue<Object> queue = new ReferenceQueue<>();
        PhantomReference<Object> phantomRef = new PhantomReference<>(new Object(), queue);

        System.out.println("GC 전: phantomRef.get() -> " + phantomRef.get() + " (항상 null)");

        System.gc();

        long maxWaitMillis = 2000; // 최대 2초까지 알림을 기다려본다
        Reference<?> notifiedRef = queue.remove(maxWaitMillis);

        if (notifiedRef == null) {
            System.out.println("2초 안에 알림을 받지 못함");
        } else {
            boolean isSamePhantom = notifiedRef == phantomRef;
            System.out.println("GC 후: 회수 알림 수신 -> 큐에서 꺼낸 참조가 phantomRef와 같은가? " + isSamePhantom);
        }
    }
}
