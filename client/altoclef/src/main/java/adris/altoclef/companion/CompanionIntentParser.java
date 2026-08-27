package adris.altoclef.companion;

import java.util.Locale;
import java.util.Optional;

/** Parses one command only. It intentionally has no fallback to AltoClef's general command parser. */
public final class CompanionIntentParser {

    public static final int MAX_COUNT = 64;

    private CompanionIntentParser() {
    }

    public static ParseResult parse(String message) {
        if (message == null || message.isBlank()) {
            return ParseResult.reject("Send one companion command.");
        }
        String trimmed = message.trim();
        if (trimmed.contains(";") || trimmed.contains("\n") || trimmed.contains("\r")) {
            return ParseResult.reject("Only one command is allowed per whisper.");
        }
        String[] parts = trimmed.split("\\s+");
        String verb = parts[0].toLowerCase(Locale.ROOT);
        return switch (verb) {
            case "follow" -> exact(parts, 1, CompanionIntent.simple(CompanionIntent.Type.FOLLOW));
            case "come" -> exact(parts, 1, CompanionIntent.simple(CompanionIntent.Type.COME));
            case "home" -> exact(parts, 1, CompanionIntent.simple(CompanionIntent.Type.HOME));
            case "protect" -> exact(parts, 1, CompanionIntent.simple(CompanionIntent.Type.PROTECT));
            case "unprotect" -> exact(parts, 1, CompanionIntent.simple(CompanionIntent.Type.UNPROTECT));
            case "status" -> exact(parts, 1, CompanionIntent.simple(CompanionIntent.Type.STATUS));
            case "queue" -> exact(parts, 1, CompanionIntent.simple(CompanionIntent.Type.QUEUE));
            case "stop" -> exact(parts, 1, CompanionIntent.simple(CompanionIntent.Type.STOP));
            case "collect" -> target(parts, CompanionIntent.Type.COLLECT, "collect <item> <count>");
            case "craft" -> target(parts, CompanionIntent.Type.CRAFT, "craft <item> <count>");
            case "smelt" -> target(parts, CompanionIntent.Type.SMELT, "smelt <item> <count>");
            case "attack" -> target(parts, CompanionIntent.Type.ATTACK, "attack <entity> <count>");
            case "give" -> target(parts, CompanionIntent.Type.GIVE, "give <item> <count>");
            case "goto" -> gotoPosition(parts);
            default -> ParseResult.reject("Unknown companion command: " + parts[0] + ".");
        };
    }

    private static ParseResult exact(String[] parts, int count, CompanionIntent intent) {
        return parts.length == count ? ParseResult.accept(intent) : ParseResult.reject("This command takes no parameters.");
    }

    private static ParseResult target(String[] parts, CompanionIntent.Type type, String usage) {
        if (parts.length != 3 || !parts[1].matches("[a-z0-9_:.]+")) {
            return ParseResult.reject("Usage: " + usage + ".");
        }
        try {
            int count = Integer.parseInt(parts[2]);
            if (count < 1 || count > MAX_COUNT) {
                return ParseResult.reject("Count must be between 1 and " + MAX_COUNT + ".");
            }
            return ParseResult.accept(CompanionIntent.target(type, parts[1].toLowerCase(Locale.ROOT), count));
        } catch (NumberFormatException ignored) {
            return ParseResult.reject("Count must be a whole number between 1 and " + MAX_COUNT + ".");
        }
    }

    private static ParseResult gotoPosition(String[] parts) {
        if (parts.length != 4) {
            return ParseResult.reject("Usage: goto <x> <y> <z>.");
        }
        try {
            return ParseResult.accept(CompanionIntent.gotoPosition(
                    Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])));
        } catch (NumberFormatException ignored) {
            return ParseResult.reject("Coordinates must be whole numbers.");
        }
    }

    public record ParseResult(Optional<CompanionIntent> intent, String error) {
        static ParseResult accept(CompanionIntent intent) {
            return new ParseResult(Optional.of(intent), null);
        }

        static ParseResult reject(String error) {
            return new ParseResult(Optional.empty(), error);
        }

        public boolean accepted() {
            return intent.isPresent();
        }
    }
}
