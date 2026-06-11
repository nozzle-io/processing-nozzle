package processing.nozzle;

public final class NozzleSelfTest {
    private NozzleSelfTest() {}

    public static void main(String[] args) {
        NozzleDiagnostics diagnostics = Nozzle.diagnostics(null);
        if (!diagnostics.nativeLoaded()) {
            throw new AssertionError(diagnostics.summary());
        }
        if (ProcessingNozzleNative.selfTest() != 0) {
            throw new AssertionError("native self-test failed");
        }
        PixelPattern.assertRgbaOracle(PixelPattern.rgba(320, 240), 320, 240);
        PixelPattern.assertRgbaOracle(PixelPattern.rgba(641, 479), 641, 479);
        System.out.println("PROCESSING_NOZZLE_SELF_TEST PASS " + diagnostics.summary());
    }
}
