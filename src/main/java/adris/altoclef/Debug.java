package adris.altoclef;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class Debug {
    public static AltoClef jankModInstance;

    public static void logInternal(String message) {
        System.err.println("COLOSSUSCRAFT: " + message);
    }

    public static void logInternal(String format, Object... args) {
        logInternal(String.format(format, args));
    }

    public static void logMessage(String message, boolean prefix) {
        Minecraft mc = Minecraft.getInstance();
        String text = prefix ? getLogPrefix() + message : message;
        if (mc.player != null) {
            mc.player.displayClientMessage(Component.literal(text), false);
        } else {
            logInternal(text);
        }
    }

    public static void logMessage(String message) {
        logMessage(message, true);
    }

    public static void logMessage(String format, Object... args) {
        logMessage(String.format(format, args));
    }

    public static void logWarning(String message) {
        logInternal("WARNING: " + message);
        if (jankModInstance == null || !jankModInstance.getModSettings().shouldHideAllWarningLogs()) {
            logMessage("[WARN] " + message);
        }
    }

    public static void logWarning(String format, Object... args) {
        logWarning(String.format(format, args));
    }

    public static void logError(String message) {
        String stacktrace = getStack(2);
        System.err.println(message);
        System.err.println(stacktrace);
        logMessage("[ERROR] " + message + "\n" + stacktrace);
    }

    public static void logError(String format, Object... args) {
        logError(String.format(format, args));
    }

    public static void logStack() {
        logInternal("STACKTRACE: \n" + getStack(2));
    }

    private static String getLogPrefix() {
        if (jankModInstance != null) {
            return jankModInstance.getModSettings().getChatLogPrefix();
        }
        return "[ColossusCraft] ";
    }

    private static String getStack(int toSkip) {
        StringBuilder stacktrace = new StringBuilder();
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            if (toSkip-- <= 0) {
                stacktrace.append(ste).append('\n');
            }
        }
        return stacktrace.toString();
    }
}
