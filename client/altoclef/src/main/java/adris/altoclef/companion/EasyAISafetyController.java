package adris.altoclef.companion;

import adris.altoclef.AltoClef;
import baritone.api.utils.input.Input;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.Box;
import adris.altoclef.multiversion.item.ItemVer;

import java.util.Comparator;
import java.util.List;

/** EasyAI-inspired real-time reflex layer; it never waits for Python/LLM. */
public final class EasyAISafetyController {
    private static final double HOSTILE_RANGE = 10.0;
    private static final double ATTACK_RANGE = 3.4;
    private static final int FOOD_THRESHOLD = 6;
    private static final int AIR_THRESHOLD = 80;
    private long lastAttackTick;
    private long tickCounter;
    private boolean eating;

    public void tick(AltoClef mod) {
        if (!AltoClef.inGame() || mod.getPlayer() == null) return;
        tickCounter++;
        ClientPlayerEntity player = mod.getPlayer();

        if (player.isSubmergedInWater() && player.getAir() <= AIR_THRESHOLD) {
            mod.getInputControls().hold(Input.JUMP);
            mod.getInputControls().release(Input.MOVE_FORWARD);
            return;
        }
        if (!player.isSubmergedInWater()) mod.getInputControls().release(Input.JUMP);

        if (player.getHealth() <= 0 || player.isInLava() || player.isOnFire()) {
            stopCombatAndEat(mod);
            return;
        }

        if (player.getHungerManager().getFoodLevel() <= FOOD_THRESHOLD && !player.isUsingItem()) {
            if (startEating(player, mod)) return;
        } else if (eating && player.getHungerManager().getFoodLevel() >= 18) {
            stopEating(mod);
        }

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
        } else {
            mod.getInputControls().release(Input.MOVE_FORWARD);
            mod.getInputControls().release(Input.CLICK_LEFT);
        }
    }

    private boolean startEating(ClientPlayerEntity player, AltoClef mod) {
        int slot = bestFood(player);
        if (slot < 0) return false;
        player.getInventory().selectedSlot = slot;
        mod.getInputControls().release(Input.MOVE_FORWARD);
        mod.getInputControls().release(Input.CLICK_LEFT);
        mod.getInputControls().hold(Input.CLICK_RIGHT);
        eating = true;
        return true;
    }

    private void stopEating(AltoClef mod) {
        mod.getInputControls().release(Input.CLICK_RIGHT);
        eating = false;
    }

    private void stopCombatAndEat(AltoClef mod) {
        mod.getInputControls().release(Input.CLICK_LEFT);
        if (eating) stopEating(mod);
    }

    private static int bestFood(ClientPlayerEntity player) {
        Item[] preferred = {Items.COOKED_BEEF, Items.COOKED_PORKCHOP, Items.BREAD,
                Items.COOKED_CHICKEN, Items.COOKED_MUTTON, Items.COOKED_RABBIT};
        for (Item wanted : preferred) for (int i = 0; i < 36; i++)
            if (player.getInventory().getStack(i).getItem() == wanted) return i;
        for (int i = 0; i < 36; i++) {
            ItemStack stack = player.getInventory().getStack(i);
            if (ItemVer.isFood(stack) && stack.getItem() != Items.POISONOUS_POTATO && stack.getItem() != Items.SPIDER_EYE) return i;
        }
        return -1;
    }

    private static void selectWeapon(ClientPlayerEntity player) {
        Item[] weapons = {Items.DIAMOND_SWORD, Items.IRON_SWORD, Items.STONE_SWORD,
                Items.WOODEN_SWORD, Items.DIAMOND_AXE, Items.IRON_AXE, Items.STONE_AXE};
        for (Item wanted : weapons) for (int i = 0; i < 9; i++)
            if (player.getInventory().getStack(i).getItem() == wanted) {
                player.getInventory().selectedSlot = i;
                return;
            }
    }

    private static void face(ClientPlayerEntity player, Entity target) {
        double dx = target.getX() - player.getX(), dy = target.getEyeY() - player.getEyeY(), dz = target.getZ() - player.getZ();
        player.setYaw((float) Math.toDegrees(Math.atan2(-dx, dz)));
        player.setPitch((float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))));
    }
}
