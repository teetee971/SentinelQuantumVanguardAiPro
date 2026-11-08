import com.sentinel.quantum.mmsjpeg.StrictJpegValidator;
import java.nio.file.*;
import java.util.Arrays;

public final class StrictJpegHostTest {
    private static int checks;
    private static void check(int expected, byte[] bytes, String name) {
        int actual = StrictJpegValidator.validate(bytes);
        if (actual != expected) throw new AssertionError(name + ": " + actual + " expected " + expected);
        checks++;
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]);
        for (String name : new String[]{"baseline", "restart", "progressive-420-12mp"})
            check(0, Files.readAllBytes(root.resolve(name + ".jpg")), name);
        check(2, Files.readAllBytes(root.resolve("progressive-444-limit.jpg")), "coefficient memory limit");
        byte[] original = Files.readAllBytes(root.resolve("baseline.jpg"));
        for (int percent : new int[]{10,25,50,75,90,99}) {
            int length = original.length * percent / 100;
            byte[] corrupt = Arrays.copyOf(original, length + 2);
            corrupt[length] = (byte)255; corrupt[length + 1] = (byte)217;
            check(1, corrupt, "artificial EOI " + percent);
        }
        check(1, null, "null input"); check(2, new byte[5 * 1024 * 1024 + 1], "input limit");
        Thread.currentThread().interrupt();
        try { check(3, original, "cancellation"); if (!Thread.currentThread().isInterrupted()) throw new AssertionError("flag cleared"); }
        finally { Thread.interrupted(); }
        System.out.println("PASS " + checks + " host JNI assertions; not physical Android tests");
    }
}
