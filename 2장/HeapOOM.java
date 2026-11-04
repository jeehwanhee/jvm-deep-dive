/**
 * 출처: 코드 2-3
 * VM 매개 변수: -Xms20m -Xmx20m -XX:+HeapDumpOnOutOfMemoryError
 * 
 * javac  HeapOOM.java
 * java -Xms20m -Xmx20m -XX:+HeapDumpOnOutOfMemoryError HeapOOM
 */
import java.util.ArrayList;
import java.util.List;

public class HeapOOM {
    static class OOMObject {
    }

    public static void main(String[] args) {
        List<OOMObject> list = new ArrayList<OOMObject>();

        while (true) {
            list.add(new OOMObject());
        }
    }
}