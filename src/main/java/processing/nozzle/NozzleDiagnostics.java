package processing.nozzle;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class NozzleDiagnostics {
    private final String processingTarget;
    private final String processingTagCommit;
    private final String javaVersion;
    private final String renderer;
    private final boolean nativeLoaded;
    private final String nativeVersion;
    private final Map<String, NozzlePathStatus> paths;

    public NozzleDiagnostics(String renderer) {
        this.processingTarget = "Processing 4.5.2 (processing-1313-4.5.2)";
        this.processingTagCommit = "c5fec0554ab6292e5984ac3e202c6577c87a42fa";
        this.javaVersion = System.getProperty("java.version", "unknown");
        this.renderer = renderer == null || renderer.isEmpty() ? "unknown" : renderer;
        this.nativeLoaded = ProcessingNozzleNative.isLoaded();
        this.nativeVersion = ProcessingNozzleNative.version();
        LinkedHashMap<String, NozzlePathStatus> values = new LinkedHashMap<>();
        values.put("java-api", NozzlePathStatus.PASS);
        values.put("jni-native-load", nativeLoaded ? NozzlePathStatus.PASS : NozzlePathStatus.FAIL);
        values.put("cpu-pattern-oracle", NozzlePathStatus.PASS);
        values.put("nozzle-cpu-frame-api", nativeLoaded ? NozzlePathStatus.PASS : NozzlePathStatus.FAIL);
        values.put("nozzle-runtime-sender-receiver", NozzlePathStatus.MISSING_HOST_SMOKE);
        values.put("pjogl-opengl-texture", NozzlePathStatus.MISSING_HOST_SMOKE);
        this.paths = Collections.unmodifiableMap(values);
    }

    public String processingTarget() { return processingTarget; }
    public String processingTagCommit() { return processingTagCommit; }
    public String javaVersion() { return javaVersion; }
    public String renderer() { return renderer; }
    public boolean nativeLoaded() { return nativeLoaded; }
    public String nativeVersion() { return nativeVersion; }
    public Map<String, NozzlePathStatus> paths() { return paths; }

    public String summary() {
        return "processing=" + processingTarget
            + " tag=" + processingTagCommit
            + " java=" + javaVersion
            + " renderer=" + renderer
            + " nativeLoaded=" + nativeLoaded
            + " nativeVersion=" + nativeVersion
            + " paths=" + paths;
    }
}
