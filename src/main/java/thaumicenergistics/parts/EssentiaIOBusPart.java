package thaumicenergistics.parts;

import appeng.api.config.RedstoneMode;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.security.IActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.parts.AEBasePart;
import appeng.util.SettingsFrom;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.storage.ContainerStorage;
import thaumicenergistics.storage.EssentiaKey;
import thaumicenergistics.storage.EssentiaTransfers;
import thaumicenergistics.storage.PoweredEssentiaStorage;

/** Adapted from ThEPartEssentiaIOBus_Base and the original import/export doWork methods. */
public final class EssentiaIOBusPart extends AEBasePart implements IGridTickable, IUpgradeableObject {
    private static final int[] SLOT_ORDER = {4, 1, 3, 5, 7, 0, 2, 6, 8};
    private static final IPartModel IMPORT_OFF = model("import", "off"), IMPORT_ON = model("import", "on");
    private static final IPartModel EXPORT_OFF = model("export", "off"), EXPORT_ON = model("export", "on");
    private static IPartModel model(String type, String state) {
        var models = List.of(ThaumicEnergistics.id("part/essentia_" + type + "_bus_" + state));
        return new IPartModel() { @Override public List<ResourceLocation> getModels() { return models; } };
    }
    private final boolean importing;
    private final String[] filters = new String[9];
    private final IUpgradeInventory upgrades;
    private RedstoneMode redstoneMode = RedstoneMode.IGNORE;
    private boolean allowVoid;
    private boolean lastRedstone;
    private boolean loading;

    public EssentiaIOBusPart(IPartItem<?> item, boolean importing) {
        super(item);
        this.importing = importing;
        Arrays.fill(filters, "");
        getMainNode().setIdlePowerUsage(0.7).setFlags(GridFlags.REQUIRE_CHANNEL).addService(IGridTickable.class, this);
        upgrades = UpgradeInventories.forMachine(item, 4, this::upgradesChanged);
    }
    private static Item upgrade(String id) { return BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath("ae2", id)); }
    public boolean importing() { return importing; }
    @Override public IUpgradeInventory getUpgrades() { return upgrades; }
    public int capacityCards() { return Math.min(2, getInstalledUpgrades(upgrade("capacity_card"))); }
    public int speedCards() { return getInstalledUpgrades(upgrade("speed_card")); }
    public int[] availableFilterSlots() { return Arrays.copyOf(SLOT_ORDER, 1 + capacityCards() * 4); }
    public String filter(int slot) { return filters[slot]; }
    public RedstoneMode redstoneMode() { return redstoneMode; }
    public boolean allowVoid() { return allowVoid; }
    public boolean hasRedstoneCard() { return isUpgradedWith(upgrade("redstone_card")); }
    @Override public boolean onUseWithoutItem(Player player, net.minecraft.world.phys.Vec3 hit) {
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && appeng.util.Platform.hasPermissions(getHost().getLocation(), player)) {
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (id, inventory, owner) -> new thaumicenergistics.menu.EssentiaBusMenu(id, inventory, this), getName()),
                    buffer -> {
                        buffer.writeBoolean(importing);
                        buffer.writeVarInt(thaumicenergistics.menu.EssentiaBusMenu.findToolSlot(player));
                    });
        }
        return true;
    }
    public static int transferAmount(int speedCards, int ticksSinceLastCall) {
        return Math.max(1, Math.min(64, (int)((4 + speedCards * 8) * (ticksSinceLastCall / 20.0F))));
    }
    private void serverMutation() {
        if (isClientSide() || !getLevel().getServer().isSameThread()) throw new IllegalStateException("Server thread required");
    }
    private void save() { if (!loading && getHost() != null) getHost().markForSave(); }
    public void filter(int slot, EssentiaKey aspect) {
        serverMutation();
        if (Arrays.stream(availableFilterSlots()).noneMatch(i -> i == slot)) throw new IllegalArgumentException("Disabled filter slot");
        filters[slot] = aspect == null ? "" : aspect.tag(); save();
    }
    public void redstoneMode(RedstoneMode mode) { serverMutation(); redstoneMode = java.util.Objects.requireNonNull(mode); save(); }
    public void allowVoid(boolean value) { serverMutation(); allowVoid = !importing && value; save(); }
    private void upgradesChanged() {
        if (upgrades == null) return;
        var enabled = availableFilterSlots();
        for (int slot = 0; slot < filters.length; slot++) {
            int index = slot;
            if (Arrays.stream(enabled).noneMatch(i -> i == index)) filters[slot] = "";
        }
        save();
    }
    public boolean acceptsFilter(EssentiaKey aspect) {
        boolean any = false;
        for (var tag : filters) if (!tag.isEmpty()) {
            any = true;
            if (tag.equals(aspect.tag())) return true;
        }
        return importing && !any;
    }
    public boolean doWork(int amount) {
        if (!getMainNode().isActive() || amount <= 0) return false;
        var grid = getMainNode().getGrid();
        if (grid == null) return false;
        var target = getBlockEntity().getBlockPos().relative(getSide());
        var container = ContainerStorage.at(getLevel(), target, allowVoid);
        if (container == null) return false;
        var network = PoweredEssentiaStorage.of(grid);
        var source = IActionSource.ofMachine(this);
        if (importing) {
            var aspect = container.dominantAspect();
            return aspect != null && acceptsFilter(aspect) && EssentiaTransfers.importFrom(container, network, aspect, amount, source) > 0;
        }
        // Original export order follows the nine physical filter positions, not insertion order.
        for (var tag : filters) if (!tag.isEmpty()) {
            var aspect = EssentiaKey.ofTag(tag);
            if (EssentiaTransfers.exportTo(network, container, aspect, amount, source) > 0) return true;
        }
        return false;
    }
    private boolean canWork() {
        if (!isUpgradedWith(upgrade("redstone_card"))) return true;
        return switch (redstoneMode) {
            case IGNORE -> true;
            case HIGH_SIGNAL -> getHost().hasRedstone();
            case LOW_SIGNAL -> !getHost().hasRedstone();
            case SIGNAL_PULSE -> false;
        };
    }
    @Override public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(10, 40, false); }
    @Override public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        return canWork() && doWork(transferAmount(speedCards(), ticksSinceLastCall)) ? TickRateModulation.URGENT : TickRateModulation.IDLE;
    }
    @Override public void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) {
        if (isClientSide()) return;
        if (redstoneMode == RedstoneMode.SIGNAL_PULSE) {
            boolean signal = getHost().hasRedstone();
            // Quirk kept: original pulse mode triggers on both transitions, using per-second amount.
            if (signal != lastRedstone) { lastRedstone = signal; doWork(4 + speedCards() * 8); }
        }
    }
    @Override public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        loading = true;
        try {
            super.readFromNBT(data, registries);
            var mode = data.getInt("redstoneMode");
            redstoneMode = mode >= 0 && mode < RedstoneMode.values().length ? RedstoneMode.values()[mode] : RedstoneMode.IGNORE;
            allowVoid = !importing && data.getBoolean("IsVoidAllowed");
            Arrays.fill(filters, "");
            for (int i = 0; i < 9; i++) filters[i] = data.getString("AspectFilter#" + i);
            upgrades.readFromNBT(data, "upgradeInventory", registries);
            upgradesChanged();
        } finally { loading = false; }
    }
    @Override public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        data.putInt("redstoneMode", redstoneMode.ordinal());
        writeFilters(data);
        upgrades.writeToNBT(data, "upgradeInventory", registries);
    }
    private void writeFilters(CompoundTag data) {
        for (int i = 0; i < 9; i++) if (!filters[i].isEmpty()) data.putString("AspectFilter#" + i, filters[i]);
        if (allowVoid) data.putBoolean("IsVoidAllowed", true);
    }
    @Override public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder) {
        super.exportSettings(mode, builder);
        var data = new CompoundTag(); writeFilters(data);
        builder.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
    }
    @Override public void importSettings(SettingsFrom mode, DataComponentMap input, Player player) {
        super.importSettings(mode, input, player);
        var data = input.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        for (int i = 0; i < 9; i++) filters[i] = data.getString("AspectFilter#" + i);
        allowVoid = !importing && data.getBoolean("IsVoidAllowed");
        upgradesChanged();
    }
    @Override public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) {
        for (var stack : upgrades) if (!stack.isEmpty()) drops.add(stack.copy());
    }
    @Override public void clearContent() { upgrades.clear(); }
    @Override public InternalInventory getSubInventory(ResourceLocation id) {
        return id.getPath().equals("upgrades") ? upgrades : super.getSubInventory(id);
    }
    @Override public int getLightLevel() { return isActive() ? 4 : 0; }
    @Override public float getCableConnectionLength(AECableType cable) { return 5; }
    @Override public void getBoxes(IPartCollisionHelper boxes) {
        // Collision helpers use the original south-facing coordinates; baked models
        // face north. Reflecting these like model JSON puts the hitbox on the wrong face.
        if (importing) {
            boxes.addBox(4,4,14,12,12,16); boxes.addBox(5,5,12,11,11,14); boxes.addBox(6,6,11,10,10,12);
        } else {
            boxes.addBox(4,4,12,12,12,13.5); boxes.addBox(5,5,13.5,11,11,15); boxes.addBox(6,6,15,10,10,16);
        }
    }
    @Override public IPartModel getStaticModels() {
        return importing ? (isActive() ? IMPORT_ON : IMPORT_OFF) : (isActive() ? EXPORT_ON : EXPORT_OFF);
    }
    @Override public boolean requireDynamicRender() { return true; }
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    @Override public void renderDynamic(float partial, com.mojang.blaze3d.vertex.PoseStack poses,
            net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay) {
        thaumicenergistics.client.EssentiaBusRendering.render(this, poses, buffers, light, overlay);
    }
}
