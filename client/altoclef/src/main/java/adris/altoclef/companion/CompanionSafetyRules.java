package adris.altoclef.companion;

import java.util.Optional;

/** Pure safety policy so its thresholds are testable without a Minecraft client. */
public final class CompanionSafetyRules {

    private static final float MINIMUM_MOVEMENT_HEALTH = 6.0F;
    private static final double DANGEROUS_FALL_SPEED = -0.7D;

    private CompanionSafetyRules() {
    }

    public static Optional<String> evaluate(boolean movementActive, boolean inLava, float health,
                                            boolean safelyGrounded, double verticalVelocity) {
        if (!movementActive) {
            return Optional.empty();
        }
        if (inLava) {
            return Optional.of("lava detected");
        }
        if (health <= MINIMUM_MOVEMENT_HEALTH) {
            return Optional.of("health is critically low");
        }
        if (!safelyGrounded && verticalVelocity < DANGEROUS_FALL_SPEED) {
            return Optional.of("dangerous fall detected");
        }
        return Optional.empty();
    }
}
