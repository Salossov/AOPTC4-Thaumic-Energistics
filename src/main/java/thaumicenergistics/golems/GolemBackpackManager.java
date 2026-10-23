package thaumicenergistics.golems;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import appeng.util.Platform;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.entities.IControllableGolem;

/**
 * Server-authoritative runtime manager for wireless golem backpack attachments,
 * network connectivity, AP tracking, and lifecycle cleanup.
 */
public final class GolemBackpackManager {
    public static class GolemConnection {
        public final UUID golemUUID;
        public ItemStack backpackStack;
        public GolemBackpackSkin skin;
        public ItemStack facadeStack;
        public IGrid grid;
        public IWirelessAccessPoint accessPoint;
        public boolean isInRange;
        public int tickCounter;

        public GolemConnection(UUID golemUUID, ItemStack backpackStack, GolemBackpackSkin skin) {
            this.golemUUID = golemUUID;
            this.backpackStack = backpackStack.copy();
            this.backpackStack.setCount(1);
            this.skin = skin != null ? skin : GolemBackpackSkin.THAUMIUM;
            this.facadeStack = ItemStack.EMPTY;
            this.isInRange = false;
            this.tickCounter = 0;
        }
    }

    private static final Map<UUID, GolemConnection> CONNECTIONS = new ConcurrentHashMap<>();

    private GolemBackpackManager() {}

    public static boolean hasBackpack(Entity golem) {
        return golem != null && CONNECTIONS.containsKey(golem.getUUID());
    }

    public static GolemConnection getConnection(Entity golem) {
        return golem != null ? CONNECTIONS.get(golem.getUUID()) : null;
    }

    public static GolemConnection getConnection(UUID uuid) {
        return uuid != null ? CONNECTIONS.get(uuid) : null;
    }

    public static Map<UUID, GolemConnection> getAllConnections() {
        return CONNECTIONS;
    }

    /**
     * Attempts to equip a Wireless Golem Backpack on a controllable golem.
     */
    public static boolean equipBackpack(LivingEntity golem, ItemStack held, Player player) {
        if (golem == null || golem.level().isClientSide || !(golem instanceof IControllableGolem cg)) {
            return false;
        }

        if (hasBackpack(golem)) {
            return false; // Already has a backpack
        }

        // Check supported core: 0=Fill, 2=Gather, 5=Liquid, 6=Essentia
        int core = cg.core();
        if (core != 0 && core != 2 && core != 5 && core != 6) {
            return false;
        }

        if (!WirelessGolemBackpackItem.isLinked(held)) {
            return false;
        }

        GolemBackpackSkin initialSkin = GolemBackpackSkin.fromMaterialName(cg.golemMaterial());
        GolemConnection conn = new GolemConnection(golem.getUUID(), held, initialSkin);

        // Resolve grid immediately
        conn.grid = WirelessGolemBackpackItem.getLinkedGrid(conn.backpackStack, golem.level(), null);
        updateAccessPoint(conn, golem);

        CONNECTIONS.put(golem.getUUID(), conn);

        if (player != null && !player.isCreative()) {
            held.shrink(1);
        }

        golem.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(),
                SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 0.5F, 1.0F);

        return true;
    }

    /**
     * Unequips the backpack, optionally dropping the items into the world.
     */
    public static boolean unequipBackpack(LivingEntity golem, boolean drop) {
        if (golem == null || golem.level().isClientSide) {
            return false;
        }

        GolemConnection conn = CONNECTIONS.remove(golem.getUUID());
        if (conn == null) {
            return false;
        }

        if (drop && golem.level() instanceof ServerLevel level) {
            if (conn.backpackStack != null && !conn.backpackStack.isEmpty()) {
                ItemEntity dropEntity = new ItemEntity(level, golem.getX(), golem.getY() + 0.5, golem.getZ(), conn.backpackStack);
                level.addFreshEntity(dropEntity);
            }
            if (conn.facadeStack != null && !conn.facadeStack.isEmpty()) {
                ItemEntity dropEntity = new ItemEntity(level, golem.getX(), golem.getY() + 0.5, golem.getZ(), conn.facadeStack);
                level.addFreshEntity(dropEntity);
            }
        }

        return true;
    }

    /**
     * Applies a facade to the equipped backpack, altering its skin.
     */
    public static boolean applyFacade(LivingEntity golem, ItemStack held, Player player) {
        if (golem == null || golem.level().isClientSide) {
            return false;
        }

        GolemConnection conn = CONNECTIONS.get(golem.getUUID());
        if (conn == null) {
            return false;
        }

        GolemBackpackSkin skin = GolemBackpackSkin.fromFacade(held);
        if (skin == null) {
            return false;
        }

        // Return old facade if present
        if (conn.facadeStack != null && !conn.facadeStack.isEmpty() && (player == null || !player.isCreative())) {
            ItemEntity dropEntity = new ItemEntity(golem.level(), golem.getX(), golem.getY() + 0.5, golem.getZ(), conn.facadeStack);
            golem.level().addFreshEntity(dropEntity);
        }

        conn.facadeStack = held.copy();
        conn.facadeStack.setCount(1);
        conn.skin = skin;

        if (player != null && !player.isCreative()) {
            held.shrink(1);
        }

        golem.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(),
                SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.NEUTRAL, 0.5F, 1.0F);

        return true;
    }

    private static int squaredDistance(IWirelessAccessPoint point, Entity golem) {
        var pos = point.getLocation().getPos();
        int dx = pos.getX() - (int) golem.getX();
        int dy = pos.getY() - (int) golem.getY();
        int dz = pos.getZ() - (int) golem.getZ();
        return dx * dx + dy * dy + dz * dz;
    }

    private static boolean isUsable(IWirelessAccessPoint point, IGrid grid, Entity golem) {
        if (point == null || point.getGrid() != grid || !point.isActive()) {
            return false;
        }
        var loc = point.getLocation();
        var level = golem.level();
        if (!loc.isInWorld(level) || !level.hasChunkAt(loc.getPos()) || level.getBlockEntity(loc.getPos()) != point) {
            return false;
        }
        return squaredDistance(point, golem) <= point.getRange() * point.getRange();
    }

    private static void updateAccessPoint(GolemConnection conn, Entity golem) {
        if (conn.grid == null) {
            conn.grid = WirelessGolemBackpackItem.getLinkedGrid(conn.backpackStack, golem.level(), null);
            if (conn.grid == null) {
                conn.accessPoint = null;
                conn.isInRange = false;
                return;
            }
        }

        if (isUsable(conn.accessPoint, conn.grid, golem)) {
            conn.isInRange = true;
            return;
        }

        conn.accessPoint = null;
        int nearestDist = Integer.MAX_VALUE;
        for (var node : conn.grid.getNodes()) {
            if (node.getOwner() instanceof IWirelessAccessPoint candidate && isUsable(candidate, conn.grid, golem)) {
                int dist = squaredDistance(candidate, golem);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    conn.accessPoint = candidate;
                }
            }
        }

        conn.isInRange = conn.accessPoint != null;
    }

    /**
     * Ticks a golem on the server, refreshing wireless status every 18 ticks.
     */
    public static void tickGolem(LivingEntity golem) {
        if (golem == null || golem.level().isClientSide) {
            return;
        }

        GolemConnection conn = CONNECTIONS.get(golem.getUUID());
        if (conn == null) {
            return;
        }

        conn.tickCounter++;
        if (conn.tickCounter > 18) {
            conn.tickCounter = 0;
            updateAccessPoint(conn, golem);
        }
    }

    public static void onEntityDeath(LivingEntity entity) {
        unequipBackpack(entity, true);
    }

    public static void onEntityLeave(Entity entity) {
        // Drop backpack if entity is removed/discarded
        if (entity instanceof LivingEntity living && living.isDeadOrDying()) {
            unequipBackpack(living, true);
        }
    }

    public static void onServerStopped() {
        CONNECTIONS.clear();
    }
}
