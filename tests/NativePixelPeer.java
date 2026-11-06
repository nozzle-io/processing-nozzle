package processing.nozzle;

public final class NativePixelPeer {
    private static native boolean check(String name, int width, int height);
    private static native long publish(String name, int width, int height);
    private static native void destroy(long sender);

    private static int argb(int x, int y) {
        return ((149 + x % 73) << 24) | ((190 + y % 31) << 16)
            | ((20 + x % 193) << 8) | (40 + y % 79);
    }

    private static void send(int width, int height, String name) {
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) pixels[y * width + x] = argb(x, y);
        }
        try (NozzleSender sender = new NozzleSender(null, name)) {
            if (sender.publishPixels(pixels, width, height) != NozzlePathStatus.PASS) {
                throw new AssertionError("publish: " + sender.lastError());
            }
            if (!check(name, width, height)) throw new AssertionError("native receiver mismatch/error");
        }
    }

    private static void receive(int width, int height, String name) {
        long nativeSender = publish(name, width, height);
        if (nativeSender == 0) throw new AssertionError("native sender failed (no SKIP)");
        try (NozzleReceiver receiver = new NozzleReceiver(null, name)) {
            if (receiver.update(width, height, 3000) != NozzlePathStatus.PASS) {
                throw new AssertionError("receive: " + receiver.lastError());
            }
            byte[] pixels = receiver.lastRgbaFrame();
            for (int y = 0; y < height; ++y) {
                for (int x = 0; x < width; ++x) {
                    int value = argb(x, y);
                    int[] rgba = {(value >>> 16) & 255, (value >>> 8) & 255, value & 255, value >>> 24};
                    for (int c = 0; c < 4; ++c) {
                        int actual = pixels[(y * width + x) * 4 + c] & 255;
                        if (actual != rgba[c]) throw new AssertionError("receive x=" + x + " y=" + y
                            + " channel=" + c + " actual=" + actual + " expected=" + rgba[c]);
                    }
                }
            }
        } finally {
            destroy(nativeSender);
        }
    }

    public static void main(String[] args) {
        if (!ProcessingNozzleNative.isLoaded()) throw new AssertionError(ProcessingNozzleNative.loadError());
        System.load(args[0]);
        int failed = 0;
        for (int[] size : new int[][] {{65, 3}, {641, 479}}) {
            for (boolean sending : new boolean[] {true, false}) {
                String label = (sending ? "java-to-native" : "native-to-java") + "-" + size[0] + "x" + size[1];
                String name = "p221-" + ProcessHandle.current().pid() + "-" + label;
                try {
                    if (sending) send(size[0], size[1], name);
                    else receive(size[0], size[1], name);
                    System.out.println(label + " PASS");
                } catch (AssertionError | RuntimeException error) {
                    ++failed;
                    System.err.println(label + " FAIL " + error);
                }
            }
        }
        System.out.println("PROCESSING_NATIVE_PIXEL_PEER failed=" + failed + " total=4");
        if (failed != 0) throw new AssertionError("native pixel peer failures=" + failed);
    }
}
