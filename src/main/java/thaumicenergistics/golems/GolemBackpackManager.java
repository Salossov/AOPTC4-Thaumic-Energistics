package thaumicenergistics.golems;

import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.IGrid;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.entities.GolemEquipmentApi;
import thaumcraft.api.entities.IControllableGolem;

/**
 * Server-authoritative runtime manager for wireless golem backpack attachments,
 * network connectivity, AP tracking, and lifecycle cleanup.
 * Uses GolemEquipmentApi for persistent equipment storage and CAS operations.
 */
public final class GolemBackpackManager {
    public static class GolemConnection {
        public final UUID golemUUID;
        public ItemStack backpackStack;
        public GolemBackpackSkin skin;
        public IGrid grid;
        public IWirelessAccessPoint accessPoint;
        public boolean isInRange;
        public int tickCounter;

        public GolemConnection(UUID golemUUID, ItemStack backpackStack, GolemBackpackSkin skin) {
            this.golemUUID = golemUUID;
            this.backpackStack = backpackStack.copy();
            this.backpackStack.setCount(1);
            this.skin = skin != null ? skin : GolemBackpackSkin.THAUMIUM;
            this.isInRange = false;
            this.tickCounter = 0;
        }
    }

    private static final Map<UUID, GolemConnection> CONNECTIONS = new ConcurrentHashMap<>();

    private GolemBackpackManager() {}

    public static boolean hasBackpack(Entity golem) {
        if (!(golem instanceof LivingEntity living) || !GolemEquipmentApi.supports(living)) {
            return false;
        }
        ItemStack equipped = GolemEquipmentApi.backpack(living);
        return !equipped.isEmpty() && equipped.getItem() instanceof WirelessGolemBackpackItem;
    }

    public static GolemConnection getConnection(Entity golem) {
        GolemConnection connection = resolveConnection(golem);
        return connection == null ? null : snapshot(connection);
    }

    private static GolemConnection resolveConnection(Entity golem) {
        if (!(golem instanceof LivingEntity living) || !(living.level() instanceof ServerLevel level)
                || !level.getServer().isSameThread() || living.isRemoved()
                || !level.hasChunkAt(living.blockPosition()) || level.getEntity(living.getUUID()) != living) {
            return null;
        }
        if (!hasBackpack(living)) {
            CONNECTIONS.remove(living.getUUID());
            return null;
        }

        ItemStack equipped = GolemEquipmentApi.backpack(living);
        GolemConnection conn = CONNECTIONS.get(living.getUUID());
        if (conn == null || !ItemStack.matches(conn.backpackStack, equipped)) {
            GolemBackpackSkin skin = WirelessGolemBackpackItem.getSkin(equipped);
            if (skin == null && living instanceof IControllableGolem cg) {
                skin = GolemBackpackSkin.fromMaterialName(cg.golemMaterial());
            }
            conn = new GolemConnection(living.getUUID(), equipped, skin);
            conn.grid = WirelessGolemBackpackItem.getLinkedGrid(equipped, living.level(), null);
            updateAccessPoint(conn, living);
            CONNECTIONS.put(living.getUUID(), conn);
        }
        return conn;
    }

    public static GolemConnection getConnection(UUID uuid) {
        GolemConnection connection = uuid != null ? CONNECTIONS.get(uuid) : null;
        return connection == null ? null : snapshot(connection);
    }

    public static Map<UUID, GolemConnection> getAllConnections() {
        var result = new java.util.HashMap<UUID, GolemConnection>();
        CONNECTIONS.forEach((uuid, connection) -> result.put(uuid, snapshot(connection)));
        return Map.copyOf(result);
    }

    private static GolemConnection snapshot(GolemConnection connection) {
        var result = new GolemConnection(connection.golemUUID, connection.backpackStack, connection.skin);
        result.grid = connection.grid;
        result.accessPoint = connection.accessPoint;
        result.isInRange = connection.isInRange;
        result.tickCounter = connection.tickCounter;
        return result;
    }

    /**
     * Attempts to equip a Wireless Golem Backpack on a controllable golem via GolemEquipmentApi.
     */
    public static boolean equipBackpack(LivingEntity golem, ItemStack held, ServerPlayer actor) {
        if (golem == null || golem.level().isClientSide || actor == null) {
            return false;
        }

        if (!GolemEquipmentApi.supports(golem) || !GolemEquipmentApi.canConfigure(golem, actor)) {
            return false;
        }

        if (!(golem instanceof IControllableGolem cg)) {
            return false;
        }

        // Check supported core: 0=Fill, 2=Gather, 5=Liquid, 6=Essentia/Alchemy
        int core = cg.core();
        if (core != 0 && core != 2 && core != 5 && core != 6) {
            return false;
        }

        if (!(held.getItem() instanceof WirelessGolemBackpackItem) || !WirelessGolemBackpackItem.isLinked(held)) {
            return false;
        }

        ItemStack current = GolemEquipmentApi.backpack(golem);
        if (!current.isEmpty()) {
            return false; // Already equipped
        }

        ItemStack toEquip = held.copyWithCount(1);
        GolemBackpackSkin initialSkin = WirelessGolemBackpackItem.getSkin(toEquip);
        if (initialSkin == null) {
            initialSkin = GolemBackpackSkin.fromMaterialName(cg.golemMaterial());
            WirelessGolemBackpackItem.setSkin(toEquip, initialSkin);
        }

        boolean success = GolemEquipmentApi.replaceBackpack(golem, actor, ItemStack.EMPTY, toEquip);
        if (!success) {
            return false;
        }

        if (!actor.isCreative()) {
            held.shrink(1);
        }

        GolemConnection conn = new GolemConnection(golem.getUUID(), toEquip, initialSkin);
        conn.grid = WirelessGolemBackpackItem.getLinkedGrid(toEquip, golem.level(), null);
        updateAccessPoint(conn, golem);
        CONNECTIONS.put(golem.getUUID(), conn);

        golem.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(),
                SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 0.5F, 1.0F);

        return true;
    }

    /**
     * Applies an AE2 Facade to the equipped backpack, altering its skin via GolemEquipmentApi CAS.
     */
    public static boolean applyFacade(LivingEntity golem, ItemStack held, ServerPlayer actor) {
        if (golem == null || golem.level().isClientSide || actor == null) {
            return false;
        }

        if (!GolemEquipmentApi.supports(golem) || !GolemEquipmentApi.canConfigure(golem, actor)) {
            return false;
        }

        GolemBackpackSkin skin = GolemBackpackSkin.fromFacade(held);
        if (skin == null) {
            return false;
        }

        ItemStack current = GolemEquipmentApi.backpack(golem);
        if (current.isEmpty() || !(current.getItem() instanceof WirelessGolemBackpackItem)) {
            return false;
        }

        ItemStack replacement = current.copy();
        CustomData.update(DataComponents.CUSTOM_DATA, replacement, tag -> {
            tag.putString("Skin", skin.getKey());
            tag.put("Facade", held.copyWithCount(1).save(golem.registryAccess()));
        });

        boolean success = GolemEquipmentApi.replaceBackpack(golem, actor, current, replacement);
        if (!success) {
            return false;
        }

        // Return old facade if present and actor not in creative
        CustomData oldData = current.get(DataComponents.CUSTOM_DATA);
        if (oldData != null && oldData.contains("Facade") && !actor.isCreative() && golem.level() instanceof ServerLevel level) {
            Optional<ItemStack> oldFacadeOpt = ItemStack.parse(golem.registryAccess(), oldData.copyTag().getCompound("Facade"));
            if (oldFacadeOpt.isPresent() && !oldFacadeOpt.get().isEmpty()) {
                ItemEntity dropEntity = new ItemEntity(level, golem.getX(), golem.getY() + 0.5, golem.getZ(), oldFacadeOpt.get());
                level.addFreshEntity(dropEntity);
            }
        }

        if (!actor.isCreative()) {
            held.shrink(1);
        }

        GolemConnection conn = CONNECTIONS.get(golem.getUUID());
        if (conn != null) {
            conn.backpackStack = replacement;
            conn.skin = skin;
        }

        golem.level().playSound(null, golem.getX(), golem.getY(), golem.getZ(),
                SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.NEUTRAL, 0.5F, 1.0F);

        return true;
    }

    private static double squaredDistance(IWirelessAccessPoint point, Entity golem) {
        var pos = point.getLocation().getPos();
        double dx = pos.getX() - golem.getX();
        double dy = pos.getY() - golem.getY();
        double dz = pos.getZ() - golem.getZ();
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
        boolean previouslyInRange = conn.isInRange;
        resolveAccessPoint(conn, golem);
        if (previouslyInRange != conn.isInRange) {
            PacketDistributor.sendToPlayersTrackingEntity(golem,
                    GolemWirelessStatusPayload.of(golem, conn.isInRange));
        }
    }

    private static void resolveAccessPoint(GolemConnection conn, Entity golem) {
        conn.grid = WirelessGolemBackpackItem.getLinkedGrid(conn.backpackStack, golem.level(), null);
        if (conn.grid == null) {
            conn.accessPoint = null;
            conn.isInRange = false;
            return;
        }

        if (isUsable(conn.accessPoint, conn.grid, golem)) {
            conn.isInRange = true;
            return;
        }

        conn.accessPoint = null;
        double nearestDist = Double.POSITIVE_INFINITY;
        for (var node : conn.grid.getNodes()) {
            if (node.getOwner() instanceof IWirelessAccessPoint candidate && isUsable(candidate, conn.grid, golem)) {
                double dist = squaredDistance(candidate, golem);
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

        GolemConnection conn = resolveConnection(golem);
        if (conn == null) {
            return;
        }

        conn.tickCounter++;
        if (conn.tickCounter > 18) {
            conn.tickCounter = 0;
            updateAccessPoint(conn, golem);
        }
    }

    /**
     * Cleans up the runtime connection cache on entity death.
     * Core ThaumicGolem natively handles equipment drops without duplicate drop in addon.
     */
    public static void onEntityDeath(LivingEntity entity) {
        if (entity != null) {
            CONNECTIONS.remove(entity.getUUID());
        }
    }

    /**
     * Cleans up the runtime connection cache on entity unload/leave.
     */
    public static void onEntityLeave(Entity entity) {
        if (entity != null) {
            CONNECTIONS.remove(entity.getUUID());
        }
    }

    public static void onServerStopped() {
        CONNECTIONS.clear();
    }
}
