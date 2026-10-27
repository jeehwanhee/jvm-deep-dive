/**
 * 출처: 코드 2-4
 * VM 매개 변수: -Xss180k
 * 
 * javac JavaVMStackSOF_1.java
 * java -Xss180k JavaVMStackSOF_1
 */
public class JavaVMStackSOF_1 {
    private int stackLength = 1;

    public void stackLeak() {
        stackLength++;
        stackLeak();
    }

    public static void main(String[] args) throws Throwable {
        JavaVMStackSOF_1 oom = new JavaVMStackSOF_1();
        try {
            oom.stackLeak();
        } catch (Throwable e) {
            System.out.println("스택 길이: " + oom.stackLength);
            throw e;
        }
    }
}