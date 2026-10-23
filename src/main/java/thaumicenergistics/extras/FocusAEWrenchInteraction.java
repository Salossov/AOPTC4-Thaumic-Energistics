package thaumicenergistics.extras;

import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.RelativeSide;
import appeng.api.parts.IPartHost;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.crafting.IInfusionPedestal;
import thaumcraft.api.wands.WandApi;

/**
 * Server-side port of the original ItemFocusAEWrench.onUse flow (release
 * 1.1.3.0 is the sole reference). Prechecks, the pedestal exclusion, the
 * part-host "block pop" through the modern AE2 wrench pipeline, the removed
 * rotateBlock adaptation and the Potentia beam all preserve the original order,
 * numbers and side semantics.
 */
public final class FocusAEWrenchInteraction {
    /**
     * Original ray trace reach: 1.7.10 Item.getMovingObjectPositionFromPlayer
     * used a fixed 4.0 distance with liquids included.
     */
    public static final double REACH = 4.0D;
    /** Original Packet_R_ParticleFX.createWrenchFX range parameter. */
    public static final float BEAM_RANGE = 20.0F;

    private static volatile ItemStack pseudoWrench;

    /** AE2 certus wrench, resolved only while the feature is available, as the original getWrench() did. */
    public static ItemStack pseudoWrench() {
        if (!ExtrasConfig.wrenchFocus()) {
            return ItemStack.EMPTY;
        }
        var wrench = pseudoWrench;
        if (wrench == null) {
            var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:certus_quartz_wrench"));
            if (item == net.minecraft.world.item.Items.AIR) {
                return ItemStack.EMPTY;
            }
            wrench = new ItemStack(item);
            pseudoWrench = wrench;
        }
        return wrench;
    }

    /**
     * Right-click focus cast. The original onFocusRightClick received the TC4
     * wand ray trace (4.0, liquids) and required a BLOCK hit. Vis reservation
     * and the cooldown belong to the core FocusBehavior pipeline; on the
     * original successful path this method spawns the Potentia beam exactly
     * where the original consumeVisAndSpawnBeam did.
     */
    public static boolean castRightClick(ServerPlayer player, ItemStack wand, ItemStack focus) {
        var hit = player.pick(REACH, 1.0F, true);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult blockHit)) {
            return false;
        }
        if (!use(player, wand, blockHit, Action.RIGHT_CLICK)) {
            return false;
        }
        spawnBeam(player, blockHit.getBlockPos());
        return true;
    }

    /**
     * The original onUse precheck chain in the original order: sneaking, wand
     * vis simulation, canMineBlock, a loaded tile entity, the pedestal
     * exclusion, then the matrix/part-host/rotate dispatch. Returns whether the
     * original handled the action; the caller owns the actual vis debit.
     */
    public static boolean use(ServerPlayer player, ItemStack wand, BlockHitResult hit, Action action) {
        var level = player.level();
        var pos = hit.getBlockPos();

        // Original: preventAction when the player is not sneaking.
        if (!player.isShiftKeyDown()) {
            return false;
        }
        // Original: wandIfValid simulated consumeAllVis(doConsume=false) before anything else.
        if (!WandApi.consume(wand, player, FocusAEWrenchItem.castCost(), false)) {
            return false;
        }
        // Original: world.canMineBlock - modern Level.mayInteract.
        if (!level.mayInteract(player, pos)) {
            return false;
        }
        var tile = level.getBlockEntity(pos);
        if (tile == null) {
            return false;
        }
        // Original: TilePedestal exclusion through the public infusion pedestal contract.
        if (tile instanceof IInfusionPedestal) {
            return false;
        }
        // Original: the ThermalExpansion TileCache right-click exception. ThermalExpansion is not
        // among the agreed dependencies; no modern equivalent is claimed (recorded in the handoff).

        boolean isPartHost = tile instanceof IPartHost;
        if (matrixProbe != null && matrixProbe.isMatrix(tile)) {
            // Original dispatch: a matrix hit never falls through to the part-host or rotate branches.
            return handleMatrixHit(player, tile, action);
        }
        if (isPartHost || action == Action.RIGHT_CLICK) {
            return blockPop(player, level, hit, action);
        }
        if (action == Action.LEFT_CLICK) {
            return rotateBlock(level, hit);
        }
        return false;
    }

    /**
     * Public-contract seam for the infusion matrix symmetry branch. The original
     * read TileInfusionMatrix.active/crafting/symmetry and forced
     * checkSurroundings; the port core exposes no public contract for that yet
     * (API request pending), so the probe stays uninstalled and the branch is a
     * no-op until the user approves the core addition.
     */
    public interface MatrixProbe {
        /** True when the tile is the core's infusion matrix. */
        boolean isMatrix(BlockEntity tile);

        /** Applies the original left-click symmetry read: refresh surroundings, return active && !crafting. */
        boolean requestSymmetry(ServerPlayer player, BlockEntity tile);
    }

    private static volatile MatrixProbe matrixProbe;

    public static void installMatrixProbe(MatrixProbe probe) {
        matrixProbe = Objects.requireNonNull(probe);
    }

    /**
     * Original handleMatrixHit: only LEFT_CLICK while the matrix is active and
     * not crafting refreshes the surroundings, reads the symmetry and reports it
     * to the player; every other action on a matrix stays unhandled.
     */
    private static boolean handleMatrixHit(ServerPlayer player, BlockEntity tile, Action action) {
        var probe = matrixProbe;
        if (probe == null || action != Action.LEFT_CLICK) {
            return false;
        }
        return probe.requestSymmetry(player, tile);
    }

    /**
     * Modern handleBlockPop: temporarily swap the pseudo wrench into the main
     * hand inside try/finally, adjust the sneak state exactly like the release
     * (sneak=false only for the part-host left-click rotate attempt; finally
     * restores the held stack and re-asserts sneaking=true as the original
     * finally did), then run the real AE2 wrench pipeline via the standard
     * game-mode use. AE2's own hook performs the exact-hit part/facade
     * dismantle (sneak) or the orientation rotate attempt, never removing more
     * than the targeted part.
     */
    private static boolean blockPop(ServerPlayer player, Level level, BlockHitResult hit, Action action) {
        var wrench = pseudoWrench();
        if (wrench.isEmpty()) {
            return false;
        }
        var pos = hit.getBlockPos();
        var prevHolding = player.getMainHandItem();
        boolean handled;
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, wrench.copy());
            if (action == Action.LEFT_CLICK) {
                player.setShiftKeyDown(false);
            }
            var result = player.gameMode.useItemOn(player, level, pseudoWrench(), InteractionHand.MAIN_HAND, hit);
            // Original: handled, or the target block no longer exists after the activation.
            handled = result.consumesAction() || level.getBlockState(pos).isAir();
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, prevHolding);
            player.setShiftKeyDown(true);
        }
        return handled;
    }

    /**
     * Modern adaptation of the removed Block.rotateBlock for the non-part-host
     * left-click: the AE2 public orientation strategy rotates around the clicked
     * face, keeping the original no-action-for-invalid-block rule. Core blocks
     * are not modified for a generic forced rotation; blocks without an
     * orientation strategy simply do nothing, as the original rotateBlock hook
     * returned false for them.
     */
    private static boolean rotateBlock(Level level, BlockHitResult hit) {
        var pos = hit.getBlockPos();
        var state = level.getBlockState(pos);
        var strategy = IOrientationStrategy.get(state);
        if (!strategy.allowsPlayerRotation()) {
            return false;
        }
        var orientation = BlockOrientation.get(strategy, state).rotateClockwiseAround(hit.getDirection());
        var newState = strategy.setOrientation(state, orientation.getSide(RelativeSide.FRONT), orientation.getSpin());
        if (newState == state || !newState.canSurvive(level, pos)) {
            return false;
        }
        level.setBlockAndUpdate(pos, newState);
        // The original fired block.onNeighborBlockChange(pos, air) after rotating; setBlockAndUpdate
        // already cascades the modern neighbor update contract (recorded as an engine adaptation).
        return true;
    }

    /**
     * Original consumeVisAndSpawnBeam visual part: broadcast the Potentia beam
     * to players within the original 20-block range. The actual vis debit is the
     * caller's (core casting pipeline for right-click, WandApi commit for the
     * left-click event).
     */
    public static void spawnBeam(ServerPlayer player, BlockPos target) {
        WrenchFocusBeamPayload.broadcast(player, target);
    }

    /** Original PlayerInteractEvent.Action values actually used by the focus. */
    public enum Action {
        LEFT_CLICK,
        RIGHT_CLICK
    }

    private FocusAEWrenchInteraction() {}
}
