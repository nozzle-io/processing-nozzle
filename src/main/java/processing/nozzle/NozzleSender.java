package processing.nozzle;

public final class NozzleSender implements AutoCloseable {
    private final Object parent;
    private final String name;
    private boolean stopped;

    public NozzleSender(Object parent, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("sender name must not be empty");
        }
        this.parent = parent;
        this.name = name;
    }

    public String name() { return name; }
    public Object parent() { return parent; }

    public byte[] deterministicRgbaFrame(int width, int height) {
        ensureOpen();
        return PixelPattern.rgba(width, height);
    }

    public NozzlePathStatus publishPixels(int[] argbPixels, int width, int height) {
        ensureOpen();
        if (argbPixels == null || argbPixels.length < width * height) {
            throw new IllegalArgumentException("argbPixels is smaller than width*height");
        }
        return NozzlePathStatus.MISSING_HOST_SMOKE;
    }

    public void stop() { stopped = true; }
    @Override public void close() { stop(); }

    private void ensureOpen() {
        if (stopped) {
            throw new IllegalStateException("sender is stopped");
        }
    }
}
