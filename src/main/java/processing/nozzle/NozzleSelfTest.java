package processing.nozzle;

public final class NozzleSelfTest {
    private NozzleSelfTest() {}

    private static void checkUtf8Names() {
        for (String bad : new String[] {"a\0b", "a\uD800", "a\uDC00", "\uD800x"}) {
            for (boolean sending : new boolean[] {true, false}) {
                try {
                    if (sending) {
                        try (NozzleSender ignored = new NozzleSender(null, bad)) {}
                    } else {
                        try (NozzleReceiver ignored = new NozzleReceiver(null, bad)) {}
                    }
                    throw new AssertionError("malformed UTF-16/NUL was not rejected by JNI");
                } catch (IllegalArgumentException error) {
                    String expected = bad.indexOf('\0') >= 0 ? "embedded NUL in name" : "unpaired surrogate in name";
                    if (error.getClass() != IllegalArgumentException.class || !expected.equals(error.getMessage())) {
                        throw new AssertionError("unexpected JNI error", error);
                    }
                }
            }
        }
        // Codec-only negative control, not evidence of corrupted native IPC.
        try {
            java.lang.reflect.Method decode = ProcessingNozzleNative.class.getDeclaredMethod("decodeUtf8", byte[].class);
            decode.setAccessible(true);
            for (byte[] bad : new byte[][] {{(byte)0xff}, {(byte)0xed, (byte)0xa0, (byte)0x80}}) {
                try {
                    decode.invoke(null, (Object)bad);
                    throw new AssertionError("malformed native UTF-8 was not rejected");
                } catch (java.lang.reflect.InvocationTargetException error) {
                    Throwable cause = error.getCause();
                    if (cause.getClass() != IllegalStateException.class
                        || !"invalid UTF-8 returned by native code".equals(cause.getMessage())) {
                        throw new AssertionError("unexpected decoder error", cause);
                    }
                }
            }
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("decoder contract unavailable", error);
        }
        long invalid = ProcessingNozzleNative.createSender("a".repeat(64));
        String error = ProcessingNozzleNative.lastError();
        ProcessingNozzleNative.destroySender(invalid);
        if (invalid != 0 || !error.endsWith("NOZZLE_ERROR_CODE=2")) {
            throw new AssertionError("64-byte name was not InvalidArgument: " + error);
        }
        String missing = "p252-missing-" + ProcessHandle.current().pid() + "-\u65E5\uD83D\uDE80";
        long absent = ProcessingNozzleNative.createReceiver(missing);
        error = ProcessingNozzleNative.lastError();
        ProcessingNozzleNative.destroyReceiver(absent);
        if (absent != 0 || !error.equals("nozzle_receiver_create " + missing + ": NOZZLE_ERROR_CODE=8")) {
            throw new AssertionError("native UTF-8 diagnostic did not round trip: " + error);
        }
        System.out.println("PROCESSING_UTF8_INVALID_AND_NATIVE_OUTPUT PASS");

        if (System.getProperty("os.name").startsWith("Linux")) {
            System.out.println("PROCESSING_UTF8_DISCOVERY SKIP: Linux default backend unavailable");
            return;
        }
        String prefix = "p252-" + ProcessHandle.current().pid() + "-";
        String name = prefix + "a".repeat(56 - prefix.length()) + "\u65E5\uD83D\uDE80";
        if (name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length != 63) throw new AssertionError("name byte count");
        long sender = ProcessingNozzleNative.createSender(name);
        if (sender == 0) throw new AssertionError("native sender unavailable (no SKIP): " + ProcessingNozzleNative.lastError());
        try {
            long receiver = ProcessingNozzleNative.createReceiver(name);
            if (receiver == 0) throw new AssertionError("native UTF-8 lookup failed: " + ProcessingNozzleNative.lastError());
            ProcessingNozzleNative.destroyReceiver(receiver);
            long tooLong = ProcessingNozzleNative.createSender(name + "a");
            error = ProcessingNozzleNative.lastError();
            ProcessingNozzleNative.destroySender(tooLong);
            if (tooLong != 0 || !error.endsWith("NOZZLE_ERROR_CODE=2")) throw new AssertionError("64-byte UTF-8 name accepted");
        } finally {
            ProcessingNozzleNative.destroySender(sender);
        }
        System.out.println("PROCESSING_UTF8_DISCOVERY PASS (metadata only; no pixel claim)");
    }

    public static void main(String[] args) {
        NozzleDiagnostics diagnostics = Nozzle.diagnostics(null);
        if (!diagnostics.nativeLoaded()) {
            throw new AssertionError(diagnostics.summary());
        }
        if (ProcessingNozzleNative.selfTest() != 0) {
            throw new AssertionError("native self-test failed");
        }
        checkUtf8Names();
        PixelPattern.assertRgbaOracle(PixelPattern.rgba(320, 240), 320, 240);
        PixelPattern.assertRgbaOracle(PixelPattern.rgba(641, 479), 641, 479);
        System.out.println("PROCESSING_NOZZLE_SELF_TEST PASS " + diagnostics.summary());
    }
}
