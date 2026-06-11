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
        System.out.println("PROCESSING_NOZZLE_BACKEND_PROBE os=" + System.getProperty("os.name")
            + " dri_exists=" + new java.io.File("/dev/dri").exists()
            + " renderD128_exists=" + new java.io.File("/dev/dri/renderD128").exists()
            + " native=" + ProcessingNozzleNative.backendDiagnostics());
        if (!diagnostics.nativeLoaded()) {
            throw new AssertionError("native helper did not load: " + diagnostics.summary());
        }
        if (ProcessingNozzleNative.selfTest() != 0) {
            throw new AssertionError("native self-test failed");
        }

        runOracleChecks(320, 240);
        runOracleChecks(641, 479);

        runFrameInteropChecks(320, 240);
        runFrameInteropChecks(641, 479);
    }

    private static void runFrameInteropChecks(int width, int height) {
        String sourceName = "processing-runtime-smoke-" + width + "x" + height;
        NozzleSender sender = new NozzleSender(null, sourceName);
        NozzleReceiver receiver = new NozzleReceiver(null, sourceName);
        try {
            byte[] rgba = sender.deterministicRgbaFrame(width, height);
            PixelPattern.assertRgbaOracle(rgba, width, height);
            int[] argb = PixelPattern.argb(width, height);
            NozzlePathStatus publishStatus = sender.publishPixels(argb, width, height);
            NozzlePathStatus receiveStatus = receiver.update(width, height, 1500L);
            if (publishStatus == NozzlePathStatus.PASS && receiveStatus == NozzlePathStatus.PASS) {
                PixelPattern.assertRgbaOracle(receiver.lastRgbaFrame(), width, height);
                System.out.println("PROCESSING_NOZZLE_FRAME_INTEROP size=" + width + "x" + height
                    + " sender=PASS receiver=PASS no_y_flip=PASS no_r_b_swap=PASS alpha=PASS byte_size_mismatch=PASS"
                    + " pjogl_texture=MISSING_HOST_SMOKE copy_cost=cpu-copy");
                return;
            }
            System.out.println("PROCESSING_NOZZLE_FRAME_INTEROP size=" + width + "x" + height
                + " sender=" + publishStatus
                + " sender_error=" + sender.lastError()
                + " receiver=" + receiveStatus
                + " receiver_error=" + receiver.lastError()
                + " pjogl_texture=MISSING_HOST_SMOKE copy_cost=UNPROVEN");
            if (publishStatus == NozzlePathStatus.FAIL || receiveStatus == NozzlePathStatus.FAIL) {
                throw new AssertionError("Processing nozzle frame interop failed for " + width + "x" + height);
            }
        } finally {
            receiver.close();
            sender.close();
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
