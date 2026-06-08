package adris.altoclef.butler;

public final class WhisperChecker {
    private WhisperChecker() {
    }

    public record MessageResult(boolean foundMessage, String username, String message) {
    }

    public static MessageResult tryParse(String botName, String template, String message) {
        if (message == null) return new MessageResult(false, "", "");
        int split = message.indexOf(':');
        if (split > 0) {
            return new MessageResult(true, message.substring(0, split).trim(), message.substring(split + 1).trim());
        }
        return new MessageResult(false, "", "");
    }
}
