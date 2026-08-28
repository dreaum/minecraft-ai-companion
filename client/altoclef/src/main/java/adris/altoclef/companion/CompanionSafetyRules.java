package adris.altoclef.companion;

import java.util.Optional;

/** Pure safety policy so its thresholds are testable without a Minecraft client. */
public final class CompanionSafetyRules {

    private static final double DANGEROUS_FALL_SPEED = -0.7D;

    private CompanionSafetyRules() {
    }

    public static Optional<String> evaluate(boolean movementActive, boolean inLava, float health,
                                            boolean safelyGrounded, double verticalVelocity) {
        return evaluate(movementActive, inLava, false, health, 20,
                false, 300, 300, false, safelyGrounded, verticalVelocity);
    }

    /**
     * Checks hazards without a reliable automatic recovery chain. Environmental hazards such as
     * water, lava, fire, and hunger are deliberately excluded: survival chains temporarily
     * preempt the user task and then let it continue.
     */
    public static Optional<String> evaluate(boolean movementActive, boolean inLava, boolean onFire,
                                            float health, int foodLevel, boolean submergedInWater,
                                            int air, int maxAir, boolean suffocating,
                                            boolean safelyGrounded, double verticalVelocity) {
        if (!movementActive) {
            return Optional.empty();
        }
        // Water, lava, fire, hunger and low health are all handled by AltoClef's
        // survival chains (SurfaceFromWaterTask / EscapeFromLavaTask / FoodChain).
        // They temporarily preempt the user task instead of cancelling it, so the
        // companion keeps making progress once it is safe again. Only hazards with
        // no reliable automatic recovery still pause the task.
        if (suffocating) {
            return Optional.of("suffocation detected");
        }
        if (!safelyGrounded && verticalVelocity < DANGEROUS_FALL_SPEED) {
            return Optional.of("dangerous fall detected");
        }
        return Optional.empty();
    }
}
