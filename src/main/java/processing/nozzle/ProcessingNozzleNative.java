package processing.nozzle;

final class ProcessingNozzleNative {
    private static final boolean loaded;
    private static final String loadError;

    static {
        boolean ok = false;
        String error = null;
        try {
            System.loadLibrary("processing_nozzle_jni");
            ok = true;
        } catch (UnsatisfiedLinkError e) {
            error = e.toString();
        }
        loaded = ok;
        loadError = error;
    }

    private ProcessingNozzleNative() {}

    static boolean isLoaded() {
        return loaded;
    }

    static String loadError() {
        return loadError;
    }

    static String version() {
        if (!loaded) {
            return "not-loaded: " + loadError;
        }
        return nativeVersion();
    }

    static int selfTest() {
        if (!loaded) {
            return -1;
        }
        return nativeSelfTest();
    }

    private static native String nativeVersion();
    private static native int nativeSelfTest();
}
