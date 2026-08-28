package adris.altoclef.companion;

import adris.altoclef.AltoClef;
import baritone.api.utils.input.Input;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;

import java.util.Comparator;
import java.util.List;

/** EasyAI-inspired real-time reflex layer; it never waits for Python/LLM. */
public final class EasyAISafetyController {
    private static final double HOSTILE_RANGE = 10.0;
    private static final double ATTACK_RANGE = 3.4;
    private static final int AIR_THRESHOLD = 80;
    private long lastAttackTick;
    private long tickCounter;

    public void tick(AltoClef mod) {
        if (!AltoClef.inGame() || mod.getPlayer() == null) return;
        tickCounter++;
        ClientPlayerEntity player = mod.getPlayer();

        // Hold jump only while actually submerged and low on air. Always release it
        // when the player surfaces, otherwise the synthetic key state leaks into normal play.
        if (player.isSubmergedInWater()) {
            if (player.getAir() <= AIR_THRESHOLD) {
                mod.getInputControls().hold(Input.JUMP);
                mod.getInputControls().release(Input.MOVE_FORWARD);
            } else {
                mod.getInputControls().release(Input.JUMP);
            }
            return;
        }
        mod.getInputControls().release(Input.JUMP);
        if (player.getHealth() <= 0 || player.isInLava() || player.isOnFire()) {
            mod.getInputControls().release(Input.CLICK_LEFT);
            return;
        }

        // FoodChain owns eating so it can preserve hunger and saturation without
        // cancelling the active movement/resource task.

        List<HostileEntity> hostiles = player.getWorld().getEntitiesByClass(
                HostileEntity.class, new Box(player.getBlockPos()).expand(HOSTILE_RANGE),
                entity -> entity.isAlive() && entity.distanceTo(player) <= HOSTILE_RANGE);
        hostiles.sort(Comparator.comparingDouble((Entity entity) -> entity.distanceTo(player)));
        if (!hostiles.isEmpty()) {
            HostileEntity target = hostiles.get(0);
            if (target.distanceTo(player) <= ATTACK_RANGE && mod.getClientBaritone().getPathingBehavior().isPathing()) {
                mod.getClientBaritone().getPathingBehavior().forceCancel();
            }
            face(player, target);
            selectWeapon(player);
            if (target.distanceTo(player) > ATTACK_RANGE) mod.getInputControls().hold(Input.MOVE_FORWARD);
            else mod.getInputControls().release(Input.MOVE_FORWARD);
            if (target.distanceTo(player) <= ATTACK_RANGE && tickCounter - lastAttackTick >= 10) {
                mod.getPlayerExtraController().attack(target);
                lastAttackTick = tickCounter;
            }
        }
    }

    /** Chooses the highest base-attack-damage melee weapon available on the hotbar. */
    private static void selectWeapon(ClientPlayerEntity player) {
        int bestSlot = -1;
        float bestDamage = -1.0F;
        for (int i = 0; i < 9; i++) {
            float damage = meleeDamage(player.getInventory().getStack(i));
            if (damage > bestDamage) {
                bestDamage = damage;
                bestSlot = i;
            }
        }
        if (bestSlot >= 0 && bestDamage > 0.0F) {
            player.getInventory().selectedSlot = bestSlot;
        }
    }

    private static float meleeDamage(ItemStack stack) {
        Item item = stack.getItem();
        if (item instanceof net.minecraft.item.ToolItem tool) return tool.getMaterial().getAttackDamage();
        return 0.0F;
    }

    private static void face(ClientPlayerEntity player, Entity target) {
        double dx = target.getX() - player.getX(), dy = target.getEyeY() - player.getEyeY(), dz = target.getZ() - player.getZ();
        player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }
}
