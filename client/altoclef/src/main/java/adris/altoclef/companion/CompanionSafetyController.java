package adris.altoclef.companion;

import adris.altoclef.AltoClef;
import adris.altoclef.ui.MessagePriority;
import net.minecraft.client.network.ClientPlayerEntity;

/** Stops companion navigation before the client continues a dangerous movement. */
public final class CompanionSafetyController {

    public void tick(AltoClef mod) {
        if (!AltoClef.inGame()) {
            return;
        }

        ClientPlayerEntity player = mod.getPlayer();
        boolean safelyGrounded = player.isOnGround() || player.isTouchingWater()
                || player.isSwimming() || player.isClimbing();
        boolean monitoring = mod.getCompanionSession().isMovementActive()
                || mod.getCompanionSession().getState() == CompanionState.SAFETY_PAUSE;
        CompanionSafetyRules.evaluate(monitoring, player.isInLava(), player.isOnFire(), player.getHealth(),
                        player.getHungerManager().getFoodLevel(), player.isSubmergedInWater(), player.getAir(),
                        player.getMaxAir(), player.isInsideWall(), safelyGrounded, player.getVelocity().y)
                .ifPresentOrElse(reason -> {
                    if (mod.getCompanionSession().getState() != CompanionState.SAFETY_PAUSE) {
                        mod.getCompanionOrchestrator().safetyPause(reason);
                        mod.logWarning("Companion stopped for safety: " + reason + ".", MessagePriority.ASAP);
                    }
                }, mod.getCompanionOrchestrator()::resumeAfterSafety);
    }
}
