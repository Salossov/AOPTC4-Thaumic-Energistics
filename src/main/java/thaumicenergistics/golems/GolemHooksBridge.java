package thaumicenergistics.golems;

import appeng.api.config.Actionable;
import net.minecraft.server.level.ServerPlayer;
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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.entities.GolemEquipmentApi;
import thaumcraft.api.entities.IControllableGolem;
import thaumicenergistics.ThaumicEnergistics;

/**
 * Event-driven bridge and hook dispatcher connecting live Thaumcraft golems with
 * Wireless Golem Backpack interactions, AP status refresh, and lifecycle events.
 * Note: Actual golem AI task replacement (Gather/Fill/Liquid/Alchemy) requires
 * core AI task hooks and remains BLOCKED_CORE_API (G3).
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
        if (!(target instanceof LivingEntity living) || !GolemEquipmentApi.supports(living) || !(target instanceof IControllableGolem)) {
            return;
        }

        Player player = event.getEntity();
        ItemStack held = event.getItemStack();

        // Client side preview: acknowledge interaction to swing hand
        if (event.getLevel().isClientSide) {
            if (held.getItem() instanceof WirelessGolemBackpackItem && GolemEquipmentApi.backpack(living).isEmpty()) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.sidedSuccess(true));
                return;
            }
            if (GolemBackpackManager.hasBackpack(living) && GolemBackpackSkin.fromFacade(held) != null) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.sidedSuccess(true));
                return;
            }
            return;
        }

        // Server side: enforce ServerPlayer and GolemEquipmentApi.canConfigure permissions
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        // 1. Equip Wireless Backpack
        if (held.getItem() instanceof WirelessGolemBackpackItem) {
            if (GolemBackpackManager.equipBackpack(living, held, serverPlayer)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.sidedSuccess(false));
                return;
            }
        }

        // 2. Apply Facade to alter skin
        if (GolemBackpackManager.hasBackpack(living) && GolemBackpackSkin.fromFacade(held) != null) {
            if (GolemBackpackManager.applyFacade(living, held, serverPlayer)) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.sidedSuccess(false));
            }
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof LivingEntity golem) {
            var connection = GolemBackpackManager.getConnection(golem);
            if (connection != null) {
                PacketDistributor.sendToPlayer(player, GolemWirelessStatusPayload.of(golem, connection.isInRange));
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
        if (!event.getEntity().level().isClientSide) {
            GolemBackpackManager.onEntityDeath(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onEntityLeaveLevel(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide) {
            GolemWirelessStatusPayload.forget(event.getEntity());
        } else {
            GolemBackpackManager.onEntityLeave(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        GolemBackpackManager.onServerStopped();
    }

    // --- Golem Action Helper Handlers (Gather, Fill, Liquid, Essentia) ---
    // Note: Pending core AI task hook integration (G3 BLOCKED_CORE_API).

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
