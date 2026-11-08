package com.sentinel.quantum.mmsjpeg;

/** Full entropy decoding with fatal libjpeg diagnostics. No Android framework or side effects. */
public final class StrictJpegValidator {
    public static final int VALID = 0, INVALID = 1, LIMIT = 2, INTERRUPTED = 3, UNAVAILABLE = 4;
    private static final boolean AVAILABLE;
    static {
        boolean available;
        try { System.loadLibrary("sentinel_jpeg_validator"); available = true; }
        catch (UnsatisfiedLinkError | SecurityException e) { available = false; }
        AVAILABLE = available;
    }
    private StrictJpegValidator() {}
    public static int validate(byte[] input) {
        if (Thread.currentThread().isInterrupted()) return INTERRUPTED;
        if (input == null || input.length < 4) return INVALID;
        if (input.length > 5 * 1024 * 1024) return LIMIT;
        try { return AVAILABLE ? validateNative(input) : UNAVAILABLE; }
        catch (OutOfMemoryError e) { return LIMIT; }
    }
    private static boolean interrupted() { return Thread.currentThread().isInterrupted(); }
    private static native int validateNative(byte[] input);
}
