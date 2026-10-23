package thaumicenergistics.extras;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.isCanceled() || event.getEntity().level().isClientSide()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // Validate player state: reject fake players and dead entities.
        if (!player.isAlive() || player instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            return;
        }
        // Sneaking is strictly required by the original onUse.
        if (!player.isShiftKeyDown()) {
            return;
        }
        // The original swing hook fired once per swing; only the dig start maps to it.
        if (event.getAction() != PlayerInteractEvent.LeftClickBlock.Action.START) {
            return;
        }
        // Verify loaded chunk without force-loading.
        var level = player.serverLevel();
        var targetPos = event.getPos();
        if (!level.hasChunkAt(targetPos)) {
            return;
        }
        // Distance check: must be within reasonable reach (< 8 blocks).
        if (player.distanceToSqr(Vec3.atCenterOf(targetPos)) >= 64.0) {
            return;
        }
        // World interaction permission check.
        if (!level.mayInteract(player, targetPos)) {
            return;
        }
        var wand = player.getItemInHand(InteractionHand.MAIN_HAND);
        // Original: the focus only acts through a valid wand or staff with this focus selected.
        if (WandApi.kind(wand) == WandApi.Kind.NONE) {
            return;
        }
        var focusStack = WandApi.focus(wand);
        if (!(focusStack.getItem() instanceof FocusAEWrenchItem)) {
            return;
        }
        // TC4 starts the native cooldown before delegating the swing, including no-op actions.
        if (!thaumcraft.api.wands.FocusSwingApi.tryBegin(player, wand)) {
            return;
        }
        // Fresh ray trace exactly like the original onEntitySwing ray trace.
        var hit = player.pick(FocusAEWrenchInteraction.REACH, 1.0F, true);
        if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK
                || !blockHit.getBlockPos().equals(targetPos)) {
            return;
        }

        // Affordability check (simulation).
        var castCost = FocusAEWrenchItem.castCost();
        if (!WandApi.consume(wand, player, castCost, false)) {
            return;
        }
        var quoted = WandApi.quote(wand, player, castCost);

        // Perform actual debit with boolean check. If payment fails, do not touch block or spawn beam.
        if (!WandApi.consume(wand, player, castCost, true)) {
            return;
        }

        boolean handled = false;
        try {
            handled = FocusAEWrenchInteraction.use(player, wand, blockHit, FocusAEWrenchInteraction.Action.LEFT_CLICK);
        } finally {
            if (!handled) {
                // Refund exact quoted vis on failure.
                for (var entry : quoted.entrySet()) {
                    int current = WandApi.stored(wand, entry.getKey());
                    int cap = WandApi.capacity(wand);
                    WandApi.set(wand, entry.getKey(), Math.min(cap, current + entry.getValue()));
                }
            }
        }

        if (handled) {
            FocusAEWrenchInteraction.spawnBeam(player, blockHit.getBlockPos());
            event.setCanceled(true);
        }
    }

    private ExtrasLeftClickHandler() {}
}
