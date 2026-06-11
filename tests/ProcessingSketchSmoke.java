package processing.nozzle;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import processing.core.PApplet;

public final class ProcessingSketchSmoke extends PApplet {
    private static final CountDownLatch done = new CountDownLatch(1);
    private static volatile Throwable failure;
    private int drawCount;

    @Override
    public void settings() {
        size(320, 240);
    }

    @Override
    public void setup() {
        System.out.println("PROCESSING_NOZZLE_SKETCH setup width=" + width + " height=" + height
            + " renderer=" + getClass().getName()
            + " os=" + System.getProperty("os.name")
            + " java=" + System.getProperty("java.version"));
    }

    @Override
    public void draw() {
        drawCount += 1;
        try {
            runSmokeChecks();
            System.out.println("PROCESSING_NOZZLE_SKETCH draw=" + drawCount + " status=PASS");
        } catch (Throwable throwable) {
            failure = throwable;
            System.out.println("PROCESSING_NOZZLE_SKETCH draw=" + drawCount + " status=FAIL reason=" + throwable);
        } finally {
            done.countDown();
            exit();
        }
    }

    @Override
    public void exitActual() {
        done.countDown();
        super.exitActual();
    }

    private void runSmokeChecks() {
        NozzleDiagnostics diagnostics = Nozzle.diagnostics(this);
        System.out.println("PROCESSING_NOZZLE_DIAGNOSTICS " + diagnostics.summary());
        if (!diagnostics.nativeLoaded()) {
            throw new AssertionError("native helper did not load: " + diagnostics.summary());
        }
        if (ProcessingNozzleNative.selfTest() != 0) {
            throw new AssertionError("native self-test failed");
        }

        runOracleChecks(320, 240);
        runOracleChecks(641, 479);

        NozzleSender sender = new NozzleSender(this, "processing-runtime-smoke");
        byte[] rgba = sender.deterministicRgbaFrame(320, 240);
        PixelPattern.assertRgbaOracle(rgba, 320, 240);
        int[] argb = PixelPattern.argb(320, 240);
        NozzlePathStatus publishStatus = sender.publishPixels(argb, 320, 240);
        sender.close();

        NozzleReceiver receiver = new NozzleReceiver(this, "processing-runtime-smoke");
        NozzlePathStatus receiveStatus = receiver.update();
        receiver.close();

        System.out.println("PROCESSING_NOZZLE_PATH_STATUS sender=" + publishStatus
            + " receiver=" + receiveStatus
            + " pjogl_texture=MISSING_HOST_SMOKE copy_cost=UNPROVEN");
        if (publishStatus != NozzlePathStatus.MISSING_HOST_SMOKE) {
            throw new AssertionError("unexpected sender status: " + publishStatus);
        }
        if (receiveStatus != NozzlePathStatus.MISSING_HOST_SMOKE) {
            throw new AssertionError("unexpected receiver status: " + receiveStatus);
        }
    }

    private static void runOracleChecks(int width, int height) {
        byte[] rgba = PixelPattern.rgba(width, height);
        PixelPattern.assertRgbaOracle(rgba, width, height);
        assertLengthMismatchDetected(rgba, width, height);
        assertMutationDetected("y-flip", yFlipped(rgba, width, height), width, height);
        assertMutationDetected("r-b-swap", redBlueSwapped(rgba), width, height);
        assertMutationDetected("alpha", alphaMutated(rgba), width, height);
        System.out.println("PROCESSING_NOZZLE_CPU_ORACLE size=" + width + "x" + height
            + " no_y_flip=PASS no_r_b_swap=PASS alpha=PASS byte_size_mismatch=PASS");
    }

    private static void assertLengthMismatchDetected(byte[] rgba, int width, int height) {
        byte[] truncated = Arrays.copyOf(rgba, rgba.length - 4);
        try {
            PixelPattern.assertRgbaOracle(truncated, width, height);
        } catch (AssertionError expected) {
            return;
        }
        throw new AssertionError("byte-size mismatch was not detected for " + width + "x" + height);
    }

    private static void assertMutationDetected(String name, byte[] mutated, int width, int height) {
        try {
            PixelPattern.assertRgbaOracle(mutated, width, height);
        } catch (AssertionError expected) {
            return;
        }
        throw new AssertionError(name + " mutation was not detected for " + width + "x" + height);
    }

    private static byte[] yFlipped(byte[] rgba, int width, int height) {
        byte[] out = new byte[rgba.length];
        int rowBytes = width * 4;
        for (int y = 0; y < height; y++) {
            System.arraycopy(rgba, y * rowBytes, out, (height - 1 - y) * rowBytes, rowBytes);
        }
        return out;
    }

    private static byte[] redBlueSwapped(byte[] rgba) {
        byte[] out = rgba.clone();
        for (int index = 0; index < out.length; index += 4) {
            byte red = out[index];
            out[index] = out[index + 2];
            out[index + 2] = red;
        }
        return out;
    }

    private static byte[] alphaMutated(byte[] rgba) {
        byte[] out = rgba.clone();
        for (int index = 3; index < out.length; index += 4) {
            out[index] = (byte) 255;
        }
        return out;
    }

    public static void main(String[] args) {
        PApplet.runSketch(new String[] { ProcessingSketchSmoke.class.getName() }, new ProcessingSketchSmoke());
        try {
            if (!done.await(45, TimeUnit.SECONDS)) {
                throw new AssertionError("Processing sketch did not exit within timeout");
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted while waiting for Processing sketch exit", interruptedException);
        }
        if (failure != null) {
            throw new AssertionError("Processing sketch smoke failed", failure);
        }
        System.out.println("PROCESSING_NOZZLE_RUNTIME_SMOKE PASS");
    }
}
