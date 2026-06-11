package processing.nozzle;

public final class NozzleSender implements AutoCloseable {
    private final Object parent;
    private final String name;
    private long nativeHandle;
    private boolean stopped;
    private String lastError = "";

    public NozzleSender(Object parent, String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("sender name must not be empty");
        }
        this.parent = parent;
        this.name = name;
        this.nativeHandle = ProcessingNozzleNative.createSender(name);
        if (nativeHandle == 0L) {
            this.lastError = ProcessingNozzleNative.lastError();
        }
    }

    public String name() { return name; }
    public Object parent() { return parent; }
    public String lastError() { return lastError; }

    public byte[] deterministicRgbaFrame(int width, int height) {
        ensureOpen();
        return PixelPattern.rgba(width, height);
    }

    public NozzlePathStatus publishPixels(int[] argbPixels, int width, int height) {
        ensureOpen();
        if (argbPixels == null || argbPixels.length < width * height) {
            throw new IllegalArgumentException("argbPixels is smaller than width*height");
        }
        if (nativeHandle == 0L) {
            return NozzlePathStatus.MISSING_HOST_SMOKE;
        }
        NozzlePathStatus status = ProcessingNozzleNative.publishArgbPixels(nativeHandle, argbPixels, width, height);
        lastError = ProcessingNozzleNative.lastError();
        return status;
    }

    public void stop() {
        if (!stopped) {
            ProcessingNozzleNative.destroySender(nativeHandle);
            nativeHandle = 0L;
            stopped = true;
        }
    }

    @Override public void close() { stop(); }

    private void ensureOpen() {
        if (stopped) {
            throw new IllegalStateException("sender is stopped");
        }
    }
}
