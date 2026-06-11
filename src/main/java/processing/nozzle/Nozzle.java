package processing.nozzle;

public final class Nozzle {
    private Nozzle() {}

    public static NozzleDiagnostics diagnostics(Object parent) {
        return new NozzleDiagnostics(rendererName(parent));
    }

    public static NozzleSource[] listSources() {
        return new NozzleSource[0];
    }

    static String rendererName(Object parent) {
        if (parent == null) {
            return "unknown";
        }
        try {
            Object graphics = parent.getClass().getField("g").get(parent);
            return graphics == null ? "unknown" : graphics.getClass().getName();
        } catch (ReflectiveOperationException | SecurityException e) {
            return parent.getClass().getName();
        }
    }
}
