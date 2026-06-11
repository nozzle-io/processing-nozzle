package processing.nozzle;

public final class NozzleSource {
    private final String name;
    private final String applicationName;

    public NozzleSource(String name, String applicationName) {
        this.name = name == null ? "" : name;
        this.applicationName = applicationName == null ? "" : applicationName;
    }

    public String name() { return name; }
    public String applicationName() { return applicationName; }
}
