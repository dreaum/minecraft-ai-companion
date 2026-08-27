package adris.altoclef.companion;

import java.util.Optional;

/** Pure safety policy so its thresholds are testable without a Minecraft client. */
public final class CompanionSafetyRules {

    private static final float MINIMUM_MOVEMENT_HEALTH = 6.0F;
    private static final int MINIMUM_MOVEMENT_FOOD = 3;
    private static final double DANGEROUS_FALL_SPEED = -0.7D;

    private CompanionSafetyRules() {
    }

    public static Optional<String> evaluate(boolean movementActive, boolean inLava, float health,
                                            boolean safelyGrounded, double verticalVelocity) {
        return evaluate(movementActive, inLava, false, health, 20,
                false, 300, 300, false, safelyGrounded, verticalVelocity);
    }

    /**
     * Checks hazards that must preempt an assigned companion task. The survival chains own the
     * recovery itself; this policy only decides when the orchestrator must yield to them.
     */
    public static Optional<String> evaluate(boolean movementActive, boolean inLava, boolean onFire,
                                            float health, int foodLevel, boolean submergedInWater,
                                            int air, int maxAir, boolean suffocating,
                                            boolean safelyGrounded, double verticalVelocity) {
        if (!movementActive) {
            return Optional.empty();
        }
        if (inLava) {
            return Optional.of("lava detected");
        }
        if (onFire) {
            return Optional.of("fire detected");
        }
        if (suffocating) {
            return Optional.of("suffocation detected");
        }
        // A companion is never asked to remain underwater. Yield as soon as the client
        // observes submersion or any loss of air, so the survival chain can leave water.
        if (submergedInWater || air < maxAir) {
            return Optional.of("drowning risk detected");
        }
        if (health <= MINIMUM_MOVEMENT_HEALTH) {
            return Optional.of("health is critically low");
        }
        if (foodLevel <= MINIMUM_MOVEMENT_FOOD) {
            return Optional.of("hunger is critically low");
        }
        if (!safelyGrounded && verticalVelocity < DANGEROUS_FALL_SPEED) {
            return Optional.of("dangerous fall detected");
        }
        return Optional.empty();
    }
}
