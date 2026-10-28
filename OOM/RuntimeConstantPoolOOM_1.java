/**
 * 출처: 코드 2-7 
 * VM 매개 변수: (JDK 7 이하) -XX:PermSize=6M -XX:MaxPermSize=6M
 * VM 매개 변수: (JDK 8 이상) -XX:MetaspaceSize=6M -XX:MaxMetaspaceSize=6M
 * 
 * javac RuntimeConstantPoolOOM_1.java
 * java -XX:MetaspaceSize=6M -XX:MaxMetaspaceSize=6M RuntimeConstantPoolOOM_1  
 * -> 에러나지 않고 무한 루프
 * java -Xmx6m RuntimeConstantPoolOOM_1
 * -> OutOfMemoryError: Java heap space
 */

import java.util.HashSet;
import java.util.Set;

public class RuntimeConstantPoolOOM_1 {
    public static void main(String[] args) {
        // 전체(full) GC가 상수 풀을 회수하지 못하도록 집합(Set)을 이용해 상수 풀의
        // 참조를 유지
        Set<String> set = new HashSet<String>();
        // short 타입의 범위면 6MB 크기의 영구 세대에서 오버플로를 일으키기 충분함
        short i = 0;

        while (true) {
            set.add(String.valueOf(i++).intern());
        }
    }
}