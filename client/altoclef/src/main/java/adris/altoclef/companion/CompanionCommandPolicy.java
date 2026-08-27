package adris.altoclef.companion;

/**
 * Compatibility facade for code which needs to ask whether a whisper is one approved companion intent.
 */
public final class CompanionCommandPolicy {

    private CompanionCommandPolicy() {
    }

    public static boolean isAllowed(String message) {
        return CompanionIntentParser.parse(message).accepted();
    }

    public static boolean startsMovement(String message) {
        return CompanionIntentParser.parse(message).intent().map(CompanionIntent::isMovement).orElse(false);
    }

    public static boolean requiresSessionOwnership(String message) {
        return CompanionIntentParser.parse(message).intent().map(intent -> !intent.isReadOnly()).orElse(true);
    }
}
