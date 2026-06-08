package adris.altoclef.util.helpers;

public final class MathsHelper {
    private MathsHelper() {
    }

    public static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
