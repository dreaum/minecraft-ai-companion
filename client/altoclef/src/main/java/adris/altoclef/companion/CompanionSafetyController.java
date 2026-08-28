package adris.altoclef.companion;

import adris.altoclef.AltoClef;
import adris.altoclef.ui.MessagePriority;
import net.minecraft.client.network.ClientPlayerEntity;

/** Handles hazards that do not have a reliable AltoClef recovery chain. */
public final class CompanionSafetyController {

    private int fallingTicks;

    public void tick(AltoClef mod) {
        if (!AltoClef.inGame()) {
            return;
        }

        ClientPlayerEntity player = mod.getPlayer();
        boolean safelyGrounded = player.isOnGround() || player.isTouchingWater()
                || player.isSwimming() || player.isClimbing();
        boolean falling = !safelyGrounded && player.getVelocity().y < -0.7D;
        fallingTicks = falling ? fallingTicks + 1 : 0;
        boolean monitoring = mod.getCompanionSession().isMovementActive()
                && (!falling || fallingTicks >= 5);
        if (mod.getCompanionSession().getState() == CompanionState.SAFETY_PAUSE) {
            monitoring = true;
        }
        CompanionSafetyRules.evaluate(monitoring, player.isInLava(), player.isOnFire(), player.getHealth(),
                        player.getHungerManager().getFoodLevel(), player.isSubmergedInWater(), player.getAir(),
                        player.getMaxAir(), player.isInsideWall(), safelyGrounded, player.getVelocity().y)
                .ifPresentOrElse(reason -> {
                    if (mod.getCompanionSession().getState() != CompanionState.SAFETY_PAUSE) {
                        mod.getCompanionOrchestrator().safetyPause(reason);
                        mod.logWarning("Companion stopped for safety: " + reason + ".", MessagePriority.ASAP);
                    }
                }, () -> {
                    fallingTicks = 0;
                    mod.getCompanionOrchestrator().resumeAfterSafety();
                });
    }
}
