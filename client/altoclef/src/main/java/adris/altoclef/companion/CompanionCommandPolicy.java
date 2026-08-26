package adris.altoclef.companion;

import java.util.Locale;
import java.util.Set;

/**
 * Limits player-issued whispers to companion actions that do not modify the world.
 * Additional actions must be reviewed here before they become remotely callable.
 */
public final class CompanionCommandPolicy {

    private static final Set<String> ALLOWED_COMMANDS = Set.of(
            "follow",
            "come",
            "home",
            "stop",
            "pause",
            "unpause",
            "status"
    );

    private CompanionCommandPolicy() {
    }

    public static boolean isAllowed(String message) {
        if (message == null) {
            return false;
        }

        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return false;
        }

        String command = trimmed.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        return ALLOWED_COMMANDS.contains(command);
    }
}
