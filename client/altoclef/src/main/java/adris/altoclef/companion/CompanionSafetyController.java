package adris.altoclef.companion;

import adris.altoclef.AltoClef;
import adris.altoclef.ui.MessagePriority;
import net.minecraft.client.network.ClientPlayerEntity;

/** Stops companion navigation before the client continues a dangerous movement. */
public final class CompanionSafetyController {

    public void tick(AltoClef mod) {
        if (!AltoClef.inGame() || !mod.getCompanionSession().isMovementActive()) {
            return;
        }

        ClientPlayerEntity player = mod.getPlayer();
        boolean safelyGrounded = player.isOnGround() || player.isTouchingWater()
                || player.isSwimming() || player.isClimbing();
        CompanionSafetyRules.evaluate(mod.getCompanionSession().isMovementActive(), player.isInLava(),
                        player.getHealth(), safelyGrounded, player.getVelocity().y)
                .ifPresent(reason -> {
                    mod.getCompanionSession().safetyPause(reason);
                    mod.logWarning("Companion stopped for safety: " + reason + ".", MessagePriority.ASAP);
                    mod.cancelUserTask();
                });
    }
}
