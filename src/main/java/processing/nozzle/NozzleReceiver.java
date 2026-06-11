package processing.nozzle;

public final class NozzleReceiver implements AutoCloseable {
    private final Object parent;
    private final String sourceName;
    private long nativeHandle;
    private byte[] lastRgbaFrame = new byte[0];
    private boolean stopped;
    private String lastError = "";

    public NozzleReceiver(Object parent, String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("source name must not be empty");
        }
        this.parent = parent;
        this.sourceName = sourceName;
        this.nativeHandle = ProcessingNozzleNative.createReceiver(sourceName);
        if (nativeHandle == 0L) {
            this.lastError = ProcessingNozzleNative.lastError();
        }
    }

    public String sourceName() { return sourceName; }
    public Object parent() { return parent; }
    public String lastError() { return lastError; }
    public byte[] lastRgbaFrame() { return lastRgbaFrame.clone(); }

    public NozzlePathStatus update() {
        ensureOpen();
        return update(320, 240, 1000L);
    }

    public NozzlePathStatus update(int width, int height, long timeoutMs) {
        ensureOpen();
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width/height must be positive");
        }
        if (nativeHandle == 0L) {
            return NozzlePathStatus.MISSING_HOST_SMOKE;
        }
        byte[] rgba = new byte[width * height * 4];
        NozzlePathStatus status = ProcessingNozzleNative.receiveRgba(nativeHandle, width, height, rgba, timeoutMs);
        lastError = ProcessingNozzleNative.lastError();
        if (status == NozzlePathStatus.PASS) {
            lastRgbaFrame = rgba;
        }
        return status;
    }

    public void stop() {
        if (!stopped) {
            ProcessingNozzleNative.destroyReceiver(nativeHandle);
            nativeHandle = 0L;
            stopped = true;
        }
    }

    @Override public void close() { stop(); }

    private void ensureOpen() {
        if (stopped) {
            throw new IllegalStateException("receiver is stopped");
        }
    }
}
