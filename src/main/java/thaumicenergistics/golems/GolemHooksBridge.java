package thaumicenergistics.golems;

import appeng.api.config.Actionable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.entities.IControllableGolem;
import thaumicenergistics.ThaumicEnergistics;

/**
 * Event-driven bridge and hook dispatcher connecting live Thaumcraft golems with
 * Wireless Golem Backpack interactions, AI tasks, and lifecycle events.
 */
@EventBusSubscriber(modid = ThaumicEnergistics.ID, bus = EventBusSubscriber.Bus.GAME)
public final class GolemHooksBridge {
    private static final GolemNetworkBridge NETWORK_BRIDGE = new GolemNetworkBridge();

    private GolemHooksBridge() {}

    public static GolemNetworkBridge getNetworkBridge() {
        return NETWORK_BRIDGE;
    }

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity living) || !(target instanceof IControllableGolem)) {
            return;
        }

        Player player = event.getEntity();
        ItemStack held = event.getItemStack();

        // 1. Equip Wireless Backpack
        if (held.getItem() instanceof WirelessGolemBackpackItem) {
            if (GolemBackpackManager.equipBackpack(living, held, player)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
                return;
            }
        }

        // 2. Apply Facade to alter skin
        if (GolemBackpackManager.hasBackpack(living) && GolemBackpackSkin.fromFacade(held) != null) {
            if (GolemBackpackManager.applyFacade(living, held, player)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
            }
        }
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof LivingEntity living && GolemBackpackManager.hasBackpack(living)) {
            GolemBackpackManager.tickGolem(living);
        }
    }

    @SubscribeEvent
    public static void onEntityDeath(LivingDeathEvent event) {
        if (GolemBackpackManager.hasBackpack(event.getEntity())) {
            GolemBackpackManager.onEntityDeath(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (GolemBackpackManager.hasBackpack(event.getEntity())) {
            GolemBackpackManager.onEntityLeave(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GolemBackpackManager.onServerStopped();
    }

    // --- Golem Action Handlers (Gather, Fill, Liquid, Essentia) ---

    /**
     * Executes the Gather action: deposits golem carried stack into the ME network.
     * @return true if any items were deposited.
     */
    public static boolean executeGather(LivingEntity golem, ItemStack carried, int orderUpgrades, boolean advanced) {
        var conn = GolemBackpackManager.getConnection(golem);
        if (conn == null || !conn.isInRange || conn.grid == null || carried == null || carried.isEmpty()) {
            return false;
        }

        int maxRate = GolemNetworkBridge.getItemRate(orderUpgrades, advanced);
        int deposited = NETWORK_BRIDGE.depositStack(conn.grid, carried, maxRate);
        return deposited > 0;
    }

    /**
     * Executes the Fill action: extracts requested stack from the ME network.
     * @return extracted ItemStack, or empty if none.
     */
    public static ItemStack executeFill(LivingEntity golem, ItemStack requested, int orderUpgrades, boolean advanced) {
        var conn = GolemBackpackManager.getConnection(golem);
        if (conn == null || !conn.isInRange || conn.grid == null || requested == null || requested.isEmpty()) {
            return ItemStack.EMPTY;
        }

        int maxRate = GolemNetworkBridge.getItemRate(orderUpgrades, advanced);
        return NETWORK_BRIDGE.extractStack(conn.grid, requested, maxRate);
    }

    /**
     * Executes the Liquid action: extracts requested fluid from the ME network.
     * @return extracted FluidStack, or empty if none.
     */
    public static FluidStack executeLiquid(LivingEntity golem, FluidStack wanted, int orderUpgrades, boolean advanced) {
        var conn = GolemBackpackManager.getConnection(golem);
        if (conn == null || !conn.isInRange || conn.grid == null || wanted == null || wanted.isEmpty()) {
            return FluidStack.EMPTY;
        }

        int maxRate = GolemNetworkBridge.getFluidRate(orderUpgrades, advanced);
        return NETWORK_BRIDGE.extractFluid(conn.grid, wanted, maxRate);
    }

    /**
     * Executes Essentia insertion into the ME network.
     */
    public static int executeEssentiaInsert(LivingEntity golem, Aspect aspect, int amount, int orderUpgrades, boolean advanced) {
        var conn = GolemBackpackManager.getConnection(golem);
        if (conn == null || !conn.isInRange || conn.grid == null || aspect == null || amount <= 0) {
            return 0;
        }

        int maxRate = GolemNetworkBridge.getEssentiaRate(orderUpgrades, advanced);
        return NETWORK_BRIDGE.insertEssentia(conn.grid, aspect, amount, maxRate);
    }

    /**
     * Executes Essentia extraction from the ME network.
     */
    public static int executeEssentiaExtract(LivingEntity golem, Aspect aspect, int amount, int orderUpgrades, boolean advanced, Actionable mode) {
        var conn = GolemBackpackManager.getConnection(golem);
        if (conn == null || !conn.isInRange || conn.grid == null || aspect == null || amount <= 0) {
            return 0;
        }

        int maxRate = GolemNetworkBridge.getEssentiaRate(orderUpgrades, advanced);
        return NETWORK_BRIDGE.extractEssentia(conn.grid, aspect, amount, maxRate, mode);
    }
}
