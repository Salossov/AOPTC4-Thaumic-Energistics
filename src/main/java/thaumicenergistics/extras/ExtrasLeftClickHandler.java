package thaumicenergistics.extras;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import thaumcraft.api.wands.WandApi;
import thaumicenergistics.ThaumicEnergistics;

/**
 * The original left-click path ran from Item.onEntitySwing on both sides; the
 * core has no swing hook for addon foci, so this subscriber reproduces it with
 * server authority. Like the original swing hook, the target comes from a fresh
 * server-side ray trace (4.0, liquids), not from client-supplied data. The
 * vanilla block damage start is cancelled on the server afterwards (the
 * original canceled the swing); the client keeps its vanilla behavior and may
 * briefly show break particles - a recorded engine adaptation.
 */
@EventBusSubscriber(modid = ThaumicEnergistics.ID)
public final class ExtrasLeftClickHandler {
    /** Per-player left-click cooldown in ticks, mirroring the core's (ms + 49) / 50 conversion. */
    private static final Map<ServerPlayer, Integer> COOLDOWN = new WeakHashMap<>();

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity().level().isClientSide() || event.isCanceled()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // The original swing hook fired once per swing; only the dig start maps to it.
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        var wand = player.getItemInHand(InteractionHand.MAIN_HAND);
        // Original: the focus only acts through a valid wand or staff with this focus selected.
        var focusStack = WandApi.focus(wand);
        if (WandApi.kind(wand) == WandApi.Kind.NONE || !(focusStack.getItem() instanceof FocusAEWrenchItem focusItem)) {
            return;
        }
        var now = player.server.getTickCount();
        if (COOLDOWN.getOrDefault(player, Integer.MIN_VALUE) > now) {
            return;
        }
        // Fresh ray trace exactly like the original onEntitySwing ray trace.
        var hit = player.pick(FocusAEWrenchInteraction.REACH, 1.0F, true);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
                || !blockHit.getBlockPos().equals(event.getPos())) {
            return;
        }
        if (!FocusAEWrenchInteraction.use(player, wand, blockHit, FocusAEWrenchInteraction.Action.LEFT_CLICK)) {
            return;
        }
        COOLDOWN.put(player, now + Math.max(1, (focusItem.getActivationCooldown(focusStack) + 49) / 50));
        // Original consumeVisAndSpawnBeam: the actual debit only happens on the successful path.
        WandApi.consume(wand, player, FocusAEWrenchItem.castCost(), true);
        FocusAEWrenchInteraction.spawnBeam(player, blockHit.getBlockPos());
        event.setCanceled(true);
    }

    private ExtrasLeftClickHandler() {}
}
