package processing.nozzle;

public enum NozzlePathStatus {
    PASS,
    FAIL,
    MISSING_HOST_SMOKE,
    UNSUPPORTED;

    static NozzlePathStatus fromNativeCode(int code) {
        NozzlePathStatus[] values = values();
        if (code < 0 || values.length <= code) {
            return FAIL;
        }
        return values[code];
    }
}
