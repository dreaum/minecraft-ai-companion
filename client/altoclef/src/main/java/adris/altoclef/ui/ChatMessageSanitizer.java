package adris.altoclef.ui;

/**
 * Removes characters that Minecraft chat or servers may reject.
 */
public final class ChatMessageSanitizer {

    private ChatMessageSanitizer() {
    }

    public static String sanitize(String value) {
        if (value == null || value.isEmpty()) return "";

        StringBuilder safe = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char current = value.charAt(index);
            if (Character.isHighSurrogate(current)) {
                if (index + 1 < value.length() && Character.isLowSurrogate(value.charAt(index + 1))) {
                    int codePoint = Character.toCodePoint(current, value.charAt(++index));
                    if (isAllowed(codePoint)) safe.appendCodePoint(codePoint);
                }
                continue;
            }
            if (Character.isLowSurrogate(current)) continue;
            if (isAllowed(current)) safe.append(current);
        }
        return safe.toString();
    }

    private static boolean isAllowed(int codePoint) {
        if (codePoint < 0x20 || codePoint == 0x7F || codePoint == 0xA7) return false;
        int type = Character.getType(codePoint);
        return type != Character.CONTROL
                && type != Character.FORMAT
                && type != Character.PRIVATE_USE
                && type != Character.UNASSIGNED
                && type != Character.SURROGATE;
    }
}
