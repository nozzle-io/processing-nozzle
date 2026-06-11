package processing.nozzle;

public final class PixelPattern {
    private PixelPattern() {}

    public static byte[] rgba(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width/height must be positive");
        }
        byte[] out = new byte[width * height * 4];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int i = (y * width + x) * 4;
                int r = x == 0 ? 255 : (x == width - 1 ? 32 : (x * 251 + y * 17) & 0xff);
                int g = y == 0 ? 64 : (y == height - 1 ? 255 : (x * 19 + y * 241) & 0xff);
                int b = (x == width / 2 && y == height / 2) ? 255 : ((x * 73 + y * 37) & 0xff);
                int a = ((x + y) % 3 == 0) ? 0 : (((x + y) % 3 == 1) ? 128 : 255);
                out[i] = (byte) r;
                out[i + 1] = (byte) g;
                out[i + 2] = (byte) b;
                out[i + 3] = (byte) a;
            }
        }
        return out;
    }

    public static int[] argb(int width, int height) {
        byte[] rgba = rgba(width, height);
        int[] out = new int[width * height];
        for (int i = 0; i < out.length; i++) {
            int r = rgba[i * 4] & 0xff;
            int g = rgba[i * 4 + 1] & 0xff;
            int b = rgba[i * 4 + 2] & 0xff;
            int a = rgba[i * 4 + 3] & 0xff;
            out[i] = (a << 24) | (r << 16) | (g << 8) | b;
        }
        return out;
    }

    public static void assertRgbaOracle(byte[] rgba, int width, int height) {
        byte[] expected = rgba(width, height);
        if (rgba.length != expected.length) {
            throw new AssertionError("length mismatch: " + rgba.length + " != " + expected.length);
        }
        int[] probes = new int[] {0, width - 1, (height - 1) * width, height * width - 1, (height / 2) * width + (width / 2)};
        for (int pixel : probes) {
            int base = pixel * 4;
            for (int c = 0; c < 4; c++) {
                if (rgba[base + c] != expected[base + c]) {
                    throw new AssertionError("pixel/channel mismatch at pixel " + pixel + " channel " + c);
                }
            }
        }
    }
}
