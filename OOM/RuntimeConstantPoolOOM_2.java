/**
 * 출처: 코드 2-7 
 * VM 매개 변수: (JDK 7 이하) -XX:PermSize=6M -XX:MaxPermSize=6M
 * VM 매개 변수: (JDK 8 이상) -XX:MetaspaceSize=6M -XX:MaxMetaspaceSize=6M
 * 
 * javac RuntimeConstantPoolOOM_2.java
 * java RuntimeConstantPoolOOM_2
 */

public class RuntimeConstantPoolOOM_2 {
    public static void main(String[] args) {
        String str1 = new StringBuilder("컴퓨터").append(" 소프트웨어").toString();
        System.out.print(str1.intern() == str1);
    }
}