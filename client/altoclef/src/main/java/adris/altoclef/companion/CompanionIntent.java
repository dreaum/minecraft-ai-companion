package adris.altoclef.companion;

/** A validated, deliberately small command surface for the companion. */
public record CompanionIntent(Type type, String target, int count, Integer x, Integer y, Integer z) {

    public enum Type {
        COLLECT,
        CRAFT,
        SMELT,
        GOTO,
        FOLLOW,
        COME,
        HOME,
        ATTACK,
        PROTECT,
        UNPROTECT,
        GIVE,
        STATUS,
        QUEUE,
        STOP
    }

    public static CompanionIntent simple(Type type) {
        return new CompanionIntent(type, null, 0, null, null, null);
    }

    public static CompanionIntent target(Type type, String target, int count) {
        return new CompanionIntent(type, target, count, null, null, null);
    }

    public static CompanionIntent gotoPosition(int x, int y, int z) {
        return new CompanionIntent(Type.GOTO, null, 0, x, y, z);
    }

    public boolean isReadOnly() {
        return type == Type.STATUS || type == Type.QUEUE;
    }

    public boolean isMovement() {
        return type == Type.GOTO || type == Type.FOLLOW || type == Type.COME || type == Type.HOME;
    }

    public int priority() {
        return switch (type) {
            case PROTECT -> 300;
            // Explicit work commands must be able to interrupt a stale movement
            // request (for example, COME stuck in a safety pause). Protection
            // remains the only higher-priority mode.
            case COLLECT, CRAFT, SMELT, ATTACK, GIVE -> 250;
            case GOTO, FOLLOW, COME, HOME -> 200;
            default -> 0;
        };
    }

    public String describe() {
        return switch (type) {
            case GOTO -> "goto " + x + " " + y + " " + z;
            case COLLECT, CRAFT, SMELT, ATTACK, GIVE -> type.name().toLowerCase() + " " + target + " " + count;
            default -> type.name().toLowerCase();
        };
    }
}
