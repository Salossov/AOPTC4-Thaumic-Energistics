package thaumicenergistics.parts;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.storage.IStorageMounts;
import appeng.api.storage.IStorageProvider;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.upgrades.UpgradeInventories;
import appeng.api.util.AECableType;
import appeng.parts.AEBasePart;
import appeng.util.SettingsFrom;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.BlockGetter;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.storage.EssentiaKey;
import thaumicenergistics.storage.EssentiaStorageBusStorage;

/** Original nine filters, one inverter, 1 AE/t idle and twenty-tick external polling. */
public final class EssentiaStorageBusPart extends AEBasePart implements IStorageProvider, IGridTickable, IUpgradeableObject {
    private static final IPartModel OFF = model("off"), ON = model("on");
    private static IPartModel model(String state) {
        var ids = List.of(ThaumicEnergistics.id("part/essentia_storage_bus_" + state));
        return new IPartModel() { @Override public List<ResourceLocation> getModels() { return ids; } };
    }
    private final String[] filters = new String[9];
    private final IUpgradeInventory upgrades;
    private final EssentiaStorageBusStorage storage = new EssentiaStorageBusStorage(this);
    private Map<EssentiaKey,Long> lastContents = Map.of();
    private int priority;
    private boolean allowVoid, loading;

    public EssentiaStorageBusPart(IPartItem<?> item) {
        super(item);
        Arrays.fill(filters, "");
        getMainNode().setIdlePowerUsage(1).setFlags(GridFlags.REQUIRE_CHANNEL)
                .addService(IStorageProvider.class, this).addService(IGridTickable.class, this);
        upgrades = UpgradeInventories.forMachine(item, 1, this::configurationChanged);
    }
    public EssentiaStorageBusStorage storage() { return storage; }
    @Override public IUpgradeInventory getUpgrades() { return upgrades; }
    public String filter(int slot) { return filters[slot]; }
    public int priority() { return priority; }
    public boolean allowVoid() { return allowVoid; }
    public boolean inverted() { return upgrades != null && upgrades.isInstalled(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:inverter_card"))); }
    public boolean hasFilters() { return Arrays.stream(filters).anyMatch(tag -> !tag.isEmpty()); }
    public boolean listed(EssentiaKey key) { return Arrays.asList(filters).contains(key.tag()); }
    public boolean allows(EssentiaKey key) { return !hasFilters() || listed(key) != inverted(); }
    private void serverMutation() {
        if (isClientSide() || !getLevel().getServer().isSameThread()) throw new IllegalStateException("Server thread required");
    }
    public void filter(int slot, EssentiaKey key) { serverMutation(); filters[slot] = key == null ? "" : key.tag(); configurationChanged(); }
    public void priority(int value) { serverMutation(); priority = value; configurationChanged(); }
    public void allowVoid(boolean value) { serverMutation(); allowVoid = value; configurationChanged(); }
    private void configurationChanged() {
        if (!loading && getHost() != null && !isClientSide()) {
            getHost().markForSave();
            IStorageProvider.requestUpdate(getMainNode());
            invalidateCache();
        }
    }
    public void invalidateCache() {
        var grid = getMainNode().getGrid();
        if (grid != null) grid.getStorageService().invalidateCache();
    }
    @Override protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (getHost() != null && !isClientSide()) {
            IStorageProvider.requestUpdate(getMainNode());
            invalidateCache();
        }
    }
    @Override public void mountInventories(IStorageMounts mounts) { if (getMainNode().isActive()) mounts.mount(storage, priority); }
    @Override public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(20,20,false); }
    @Override public TickRateModulation tickingRequest(IGridNode node, int ticks) {
        var now = new LinkedHashMap<EssentiaKey,Long>();
        storage.getAvailableStacks().forEach(entry -> {
            if (entry.getKey() instanceof EssentiaKey key) now.put(key, entry.getLongValue());
        });
        if (!now.equals(lastContents)) { lastContents = now; invalidateCache(); }
        return TickRateModulation.SAME;
    }
    @Override public void onNeighborChanged(BlockGetter level, BlockPos pos, BlockPos neighbor) { if (!isClientSide()) invalidateCache(); }
    @Override public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        loading = true;
        try {
            super.readFromNBT(data,registries);
            priority = data.getInt("Priority"); allowVoid = data.getBoolean("IsVoidAllowed");
            for (int i = 0; i < 9; i++) filters[i] = data.getString("FilterAspects#" + i);
            upgrades.readFromNBT(data,"UpgradeInventory",registries);
        } finally { loading = false; }
    }
    private void writeSettings(CompoundTag data, boolean world) {
        boolean any = false;
        for (int i = 0; i < 9; i++) if (!filters[i].isEmpty()) { data.putString("FilterAspects#" + i, filters[i]); any = true; }
        // Original wrench settings omit priority/void when there are no filters.
        if (world || any) { data.putInt("Priority",priority); data.putBoolean("IsVoidAllowed",allowVoid); }
    }
    @Override public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data,registries); writeSettings(data,true);
        upgrades.writeToNBT(data,"UpgradeInventory",registries);
    }
    @Override public void exportSettings(SettingsFrom mode, DataComponentMap.Builder builder) {
        super.exportSettings(mode,builder);
        var data = new CompoundTag(); writeSettings(data,false);
        builder.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
    }
    @Override public void importSettings(SettingsFrom mode, DataComponentMap components, Player player) {
        super.importSettings(mode,components,player);
        var data = components.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        for (int i = 0; i < 9; i++) filters[i] = data.getString("FilterAspects#" + i);
        priority = data.getInt("Priority"); allowVoid = data.getBoolean("IsVoidAllowed"); configurationChanged();
    }
    @Override public void addAdditionalDrops(List<ItemStack> drops, boolean wrenched) { for (var stack : upgrades) if (!stack.isEmpty()) drops.add(stack.copy()); }
    @Override public void clearContent() { upgrades.clear(); }
    @Override public appeng.api.inventories.InternalInventory getSubInventory(ResourceLocation id) { return id.getPath().equals("upgrades") ? upgrades : super.getSubInventory(id); }
    @Override public int getLightLevel() { return 0; }
    @Override public float getCableConnectionLength(AECableType cable) { return 3; }
    @Override public void getBoxes(IPartCollisionHelper boxes) { boxes.addBox(1,1,0,15,15,1); boxes.addBox(4,4,1,12,12,2); boxes.addBox(5,5,2,11,11,3); }
    @Override public IPartModel getStaticModels() { return isActive() ? ON : OFF; }
}
