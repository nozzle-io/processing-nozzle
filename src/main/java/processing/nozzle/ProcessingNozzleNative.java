package processing.nozzle;

import java.io.File;

final class ProcessingNozzleNative {
    private static final boolean loaded;
    private static final String loadError;

    static {
        boolean ok = false;
        String error = null;
        try {
            loadBundledNozzle();
            System.loadLibrary("processing_nozzle_jni");
            ok = true;
        } catch (UnsatisfiedLinkError e) {
            error = e.toString();
        }
        loaded = ok;
        loadError = error;
    }

    private ProcessingNozzleNative() {}

    private static void loadBundledNozzle() {
        String libraryPath = System.getProperty("java.library.path", "");
        String[] entries = libraryPath.split(File.pathSeparator);
        String[] candidates = new String[] {
            System.mapLibraryName("nozzle"),
            "nozzle.dll",
            "libnozzle.dylib",
            "libnozzle.so",
        };
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) {
                continue;
            }
            File directory = new File(entry);
            for (String candidate : candidates) {
                File library = new File(directory, candidate);
                if (library.isFile()) {
                    System.load(library.getAbsolutePath());
                    return;
                }
            }
        }
    }

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

    static String backendDiagnostics() {
        if (!loaded) {
            return "not-loaded: " + loadError;
        }
        return nativeBackendDiagnostics();
    }

    static int selfTest() {
        if (!loaded) {
            return -1;
        }
        return nativeSelfTest();
    }

    static String lastError() {
        if (!loaded) {
            return loadError;
        }
        return nativeLastError();
    }

    static long createSender(String name) {
        if (!loaded) {
            return 0L;
        }
        return nativeCreateSender(name);
    }

    static void destroySender(long handle) {
        if (loaded && handle != 0L) {
            nativeDestroySender(handle);
        }
    }

    static NozzlePathStatus publishArgbPixels(long handle, int[] argbPixels, int width, int height) {
        if (!loaded) {
            return NozzlePathStatus.FAIL;
        }
        return NozzlePathStatus.fromNativeCode(nativePublishArgbPixels(handle, argbPixels, width, height));
    }

    static long createReceiver(String sourceName) {
        if (!loaded) {
            return 0L;
        }
        return nativeCreateReceiver(sourceName);
    }

    static void destroyReceiver(long handle) {
        if (loaded && handle != 0L) {
            nativeDestroyReceiver(handle);
        }
    }

    static NozzlePathStatus receiveRgba(long handle, int width, int height, byte[] outRgba, long timeoutMs) {
        if (!loaded) {
            return NozzlePathStatus.FAIL;
        }
        return NozzlePathStatus.fromNativeCode(nativeReceiveRgba(handle, width, height, outRgba, timeoutMs));
    }

    private static native String nativeVersion();
    private static native String nativeBackendDiagnostics();
    private static native int nativeSelfTest();
    private static native String nativeLastError();
    private static native long nativeCreateSender(String name);
    private static native void nativeDestroySender(long handle);
    private static native int nativePublishArgbPixels(long handle, int[] argbPixels, int width, int height);
    private static native long nativeCreateReceiver(String sourceName);
    private static native void nativeDestroyReceiver(long handle);
    private static native int nativeReceiveRgba(long handle, int width, int height, byte[] outRgba, long timeoutMs);
}
