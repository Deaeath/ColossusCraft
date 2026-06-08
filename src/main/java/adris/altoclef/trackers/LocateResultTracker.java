package adris.altoclef.trackers;

import net.minecraft.core.BlockPos;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LocateResultTracker {
    private static final Pattern BRACKET_COORDS = Pattern.compile("\\[\\s*(-?\\d+)\\s*,\\s*(?:~|-?\\d+)\\s*,\\s*(-?\\d+)\\s*\\]");
    private static String lastMessage = "";
    private static BlockPos lastStructurePos;

    private LocateResultTracker() {
    }

    public static void acceptChat(String message) {
        if (message == null) return;
        Matcher matcher = BRACKET_COORDS.matcher(message);
        if (matcher.find()) {
            int x = Integer.parseInt(matcher.group(1));
            int z = Integer.parseInt(matcher.group(2));
            lastMessage = message;
            lastStructurePos = new BlockPos(x, 64, z);
        }
    }

    public static Optional<BlockPos> lastStructurePos() {
        return Optional.ofNullable(lastStructurePos);
    }

    public static String lastMessage() {
        return lastMessage;
    }

    public static void clear() {
        lastMessage = "";
        lastStructurePos = null;
    }
}
