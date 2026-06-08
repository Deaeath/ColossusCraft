package adris.altoclef.platform;

public interface AltoClefPlatform {
    boolean playerReady();

    long tickCount();

    void log(String message);

    void stopPathing();

    boolean runBaritone(String command);
}
