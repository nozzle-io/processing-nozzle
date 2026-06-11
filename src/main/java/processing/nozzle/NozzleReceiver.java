package processing.nozzle;

public final class NozzleReceiver implements AutoCloseable {
    private final Object parent;
    private final String sourceName;
    private boolean stopped;

    public NozzleReceiver(Object parent, String sourceName) {
        if (sourceName == null || sourceName.isBlank()) {
            throw new IllegalArgumentException("source name must not be empty");
        }
        this.parent = parent;
        this.sourceName = sourceName;
    }

    public String sourceName() { return sourceName; }
    public Object parent() { return parent; }

    public NozzlePathStatus update() {
        ensureOpen();
        return NozzlePathStatus.MISSING_HOST_SMOKE;
    }

    public void stop() { stopped = true; }
    @Override public void close() { stop(); }

    private void ensureOpen() {
        if (stopped) {
            throw new IllegalStateException("receiver is stopped");
        }
    }
}
