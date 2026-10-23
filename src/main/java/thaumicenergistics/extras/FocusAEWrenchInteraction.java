package thaumicenergistics.extras;

import appeng.api.orientation.BlockOrientation;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.orientation.RelativeSide;
import appeng.api.parts.IPartHost;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.crafting.IInfusionPedestal;
import thaumcraft.api.wands.WandApi;

/**
 * Server-side port of the original ItemFocusAEWrench.onUse flow (release
 * 1.1.3.0 is the sole reference). Prechecks, the pedestal exclusion, the
 * infusion matrix branch, the part-host "block pop" through the modern AE2
 * wrench pipeline, the removed rotateBlock adaptation and the Potentia beam
 * all preserve the original order, numbers and side semantics.
 */
public final class FocusAEWrenchInteraction {
    /**
     * Original ray trace reach: 1.7.10 Item.getMovingObjectPositionFromPlayer
     * used a fixed 4.0 distance with liquids included.
     */
    public static final double REACH = 4.0D;
    /** Original Packet_R_ParticleFX.createWrenchFX range parameter. */
    public static final float BEAM_RANGE = 20.0F;

    /** Unified cooldown tracker across right-click and left-click interactions. */
    private static final Map<ServerPlayer, Long> COOLDOWNS = new WeakHashMap<>();

    public static boolean isOnCooldown(ServerPlayer player) {
        return COOLDOWNS.getOrDefault(player, Long.MIN_VALUE) > player.server.getTickCount();
    }

    public static void applyCooldown(ServerPlayer player, int cooldownMs) {
        long now = player.server.getTickCount();
        COOLDOWNS.put(player, now + Math.max(1, (cooldownMs + 49) / 50));
    }

    /**
     * Creates a fresh AE2 certus wrench item stack when the feature is enabled.
     * Never returns a mutable shared reference.
     */
    public static ItemStack createWrench() {
        if (!ExtrasConfig.wrenchFocus()) {
            return ItemStack.EMPTY;
        }
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:certus_quartz_wrench"));
        if (item == net.minecraft.world.item.Items.AIR) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item);
    }

    /**
     * Backward-compatible helper: returns a fresh copy of the AE2 certus wrench,
     * avoiding mutable shared cache exposure.
     */
    public static ItemStack pseudoWrench() {
        return createWrench();
    }

    /**
     * Identifies Thaumcraft's Runic Matrix block via public registry keys,
     * protecting it from being popped by a wrench.
     */
    public static boolean isMatrix(BlockState state) {
        var key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return "thaumcraft".equals(key.getNamespace())
                && ("infusion_matrix".equals(key.getPath()) || "infusion_matrix_climb".equals(key.getPath()));
    }

    /**
     * Right-click focus cast. The original onFocusRightClick received the TC4
     * wand ray trace (4.0, liquids) and required a BLOCK hit. Vis reservation
     * and cooldown belong to the core FocusBehavior pipeline; on the
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
     * The original onUse precheck chain in the original order: sneaking, valid
     * player, loaded chunk, reach distance, canMineBlock, a loaded tile entity,
     * the pedestal exclusion, then the matrix/part-host/rotate dispatch.
     * Returns whether the action was handled.
     *
     * Note: Vis check is not duplicated here because right-click vis is already
     * debited/reserved by the core FocusCasting pipeline, and left-click vis is
     * authoritatively validated and debited with refund by ExtrasLeftClickHandler.
     */
    public static boolean use(ServerPlayer player, ItemStack wand, BlockHitResult hit, Action action) {
        var level = player.serverLevel();
        var pos = hit.getBlockPos();

        // Original: preventAction when the player is not sneaking.
        if (!player.isShiftKeyDown()) {
            return false;
        }
        // Player state validation: must be alive, not a fake player.
        if (!player.isAlive() || player instanceof net.neoforged.neoforge.common.util.FakePlayer) {
            return false;
        }
        // Chunk must be loaded; never force-load chunks.
        if (!level.hasChunkAt(pos)) {
            return false;
        }
        // Distance check: must be within reach (< 8 blocks).
        if (player.distanceToSqr(Vec3.atCenterOf(pos)) >= 64.0) {
            return false;
        }
        // Verify held wand and focus identity.
        var held = player.getMainHandItem();
        if (held != wand || WandApi.kind(held) == WandApi.Kind.NONE
                || !(WandApi.focus(held).getItem() instanceof FocusAEWrenchItem)) {
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

        // Infusion Matrix branch: never fall through to blockPop or rotateBlock!
        boolean isMatrixBlock = isMatrix(level.getBlockState(pos)) || (matrixProbe != null && matrixProbe.isMatrix(tile));
        if (isMatrixBlock) {
            return handleMatrixHit(player, tile, action);
        }

        boolean isPartHost = tile instanceof IPartHost;
        if (isPartHost || action == Action.RIGHT_CLICK) {
            return blockPop(player, level, hit, action);
        }
        if (action == Action.LEFT_CLICK) {
            return rotateBlock(level, hit);
        }
        return false;
    }

    /**
     * Public-contract seam for the infusion matrix symmetry branch (B2-MATRIX).
     * The original read TileInfusionMatrix.active/crafting/symmetry and forced
     * checkSurroundings; the port core exposes no public contract for that yet
     * (API request pending B2-MATRIX), so the probe stays uninstalled and the
     * branch is a safe no-op until the user approves the core addition.
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
     * to the player; every other action on a matrix stays unhandled (safe no-op).
     * Never dismantles or rotates the matrix.
     */
    private static boolean handleMatrixHit(ServerPlayer player, BlockEntity tile, Action action) {
        var probe = matrixProbe;
        if (probe == null || action != Action.LEFT_CLICK) {
            return false;
        }
        return probe.requestSymmetry(player, tile);
    }

    /**
     * Modern handleBlockPop: temporarily swap a fresh temporary wrench into
     * the main hand inside a protected try/finally, adjust the sneak state
     * exactly like the release (sneak=false only for part-host left-click rotate
     * attempt), then run the real AE2 wrench pipeline via standard game-mode use.
     * The finally block guarantees complete restoration of the held stack and
     * sneak state under all outcomes and exceptions.
     */
    private static boolean blockPop(ServerPlayer player, Level level, BlockHitResult hit, Action action) {
        ItemStack tempWrench = createWrench();
        if (tempWrench.isEmpty()) {
            return false;
        }
        var pos = hit.getBlockPos();
        var prevHolding = player.getMainHandItem();
        boolean prevSneaking = player.isShiftKeyDown();
        boolean handled;
        try {
            player.setItemInHand(InteractionHand.MAIN_HAND, tempWrench);
            if (action == Action.LEFT_CLICK) {
                player.setShiftKeyDown(false);
            }
            var result = player.gameMode.useItemOn(player, level, tempWrench, InteractionHand.MAIN_HAND, hit);
            // Original: handled, or the target block no longer exists after the activation.
            handled = result.consumesAction() || level.getBlockState(pos).isAir();
        } finally {
            player.setItemInHand(InteractionHand.MAIN_HAND, prevHolding);
            player.setShiftKeyDown(prevSneaking);
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
