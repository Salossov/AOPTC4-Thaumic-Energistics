package thaumicenergistics.parts;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.AECableType;
import appeng.items.tools.MemoryCardItem;
import appeng.parts.AEBasePart;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.visnet.TileVisNode;
import thaumcraft.api.visnet.VirtualSourceRegistration;
import thaumcraft.api.visnet.VirtualVisSource;
import thaumcraft.api.visnet.VisNetHandler;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.api.DigiVisSource;
import thaumicenergistics.vis.DigiVisSourceData;

/** Original memory-card-linked relay endpoints: zero idle power, four AE per successful request. */
public final class VisInterfacePart extends AEBasePart implements IGridTickable, DigiVisSource {
    private static final Component SOURCE_NAME = Component.translatable("thaumicenergistics.gui.digivis.source.name");
    private static final IPartModel OFF = model("off"), ON = model("on");
    private static IPartModel model(String state) {
        return new IPartModel() { @Override public List<ResourceLocation> getModels() { return List.of(ThaumicEnergistics.id("part/vis_interface_"+state)); } };
    }
    private long uid = ThreadLocalRandom.current().nextLong();
    private boolean provider, consuming;
    private DigiVisSourceData linkedSource;
    private VirtualSourceRegistration registration;
    private int drainColor;
    private long lastColorUpdate;
    private final VirtualVisSource virtualSource = new VirtualVisSource() {
        @Override public int range() { return 1; }
        @Override public byte attunement() { var relay = getRelay(); return relay == null ? -1 : relay.getAttunement(); }
        @Override public boolean valid() { return current() && provider && isActive() && getRelay() != null && source() != null; }
        @Override public int drain(Aspect primal, int requestedCentivis) { return consumeVis(primal,requestedCentivis); }
    };

    public VisInterfacePart(IPartItem<?> item) {
        super(item);
        getMainNode().setIdlePowerUsage(0).setFlags(GridFlags.REQUIRE_CHANNEL).addService(IGridTickable.class,this);
    }
    @Override public IGrid getGrid() { return getMainNode().getGrid(); }
    @Override public GlobalPos getLocation() { return GlobalPos.of(getLevel().dimension(),getBlockEntity().getBlockPos()); }
    @Override public long getUID() { return uid; }
    public boolean isVisProvider() { return provider; }
    public int drainColor() { return drainColor; }
    private boolean current() {
        var owner = getBlockEntity();
        return owner != null && getHost() != null && getSide() != null && !owner.isRemoved()
                && getLevel() != null && !isClientSide() && getLevel().hasChunkAt(owner.getBlockPos())
                && getLevel().getBlockEntity(owner.getBlockPos()) == owner && getHost().getPart(getSide()) == this;
    }
    public TileVisNode getRelay() {
        if (!current()) return null;
        var pos = getBlockEntity().getBlockPos().relative(getSide());
        if (!getLevel().hasChunkAt(pos)) return null;
        var state = getLevel().getBlockState(pos);
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(ResourceLocation.parse("thaumcraft:vis_relay"))
                && state.hasProperty(BlockStateProperties.FACING) && state.getValue(BlockStateProperties.FACING) == getSide()
                && getLevel().getBlockEntity(pos) instanceof TileVisNode relay && !relay.isRemoved() ? relay : null;
    }
    private VisInterfacePart source() {
        var source = linkedSource == null || getLevel() == null ? null : linkedSource.resolve(getLevel().getServer(),getGrid());
        return source instanceof VisInterfacePart part && part != this && part.current() && !part.provider ? part : null;
    }
    @Override public int consumeVis(Aspect primal, int amount) {
        if (amount <= 0 || primal == null || !primal.isPrimal() || !current() || !isActive() || consuming) return 0;
        if (!getLevel().getServer().isSameThread()) throw new IllegalStateException("Server thread required");
        consuming = true;
        try {
            int received;
            if (provider) { var source = source(); received = source == null ? 0 : source.consumeVis(primal,amount); }
            else {
                var relay = getRelay(); var grid = getGrid();
                if (relay == null || grid == null) return 0;
                var energy = grid.getEnergyService();
                if (energy.extractAEPower(4,Actionable.SIMULATE,PowerMultiplier.CONFIG) < 4) return 0;
                received = relay.consumeVis(primal,amount);
                if (received > 0) energy.extractAEPower(4,Actionable.MODULATE,PowerMultiplier.CONFIG);
            }
            if (received > 0) color(primal.getColor());
            return received;
        } finally { consuming = false; }
    }
    private void color(int value) {
        long now = System.currentTimeMillis();
        if (value != 0) {
            if (value == drainColor) { lastColorUpdate = now; return; }
            if (now-lastColorUpdate <= 250) return;
            lastColorUpdate = now;
        }
        drainColor = value; getHost().markForUpdate();
    }
    private void closeSource() { if (registration != null) { registration.close(); registration = null; } }
    private void refreshSource() {
        if (!virtualSource.valid()) { closeSource(); return; }
        if (registration != null) {
            // The core may already have closed it after a transient owner/grid invalidation.
            try { registration.refresh(); return; } catch (IllegalStateException closed) { registration = null; }
        }
        registration = VisNetHandler.registerVirtualSource(getBlockEntity(),getSide(),virtualSource);
    }
    private void providing(boolean value) {
        closeSource(); provider = value;
        if (!value) linkedSource = null;
        getHost().markForSave();
    }
    @Override public boolean useStandardMemoryCard() { return false; }
    @Override public boolean onUseItemOn(ItemStack held, Player player, InteractionHand hand, Vec3 hit) {
        if (!(held.getItem() instanceof IMemoryCard card)) return false;
        if (isClientSide()) return true;
        if (!current()) return true;
        if (player.isShiftKeyDown()) {
            var identity = DigiVisSourceData.of(this);
            MemoryCardItem.clearCard(held);
            held.set(AEComponents.EXPORTED_SETTINGS_SOURCE,SOURCE_NAME);
            held.set(DataComponents.CUSTOM_DATA,CustomData.of(identity.write()));
            providing(false); card.notifyUser(player,MemoryCardMessages.SETTINGS_SAVED);
        } else if (SOURCE_NAME.equals(held.get(AEComponents.EXPORTED_SETTINGS_SOURCE))) {
            linkedSource = DigiVisSourceData.read(held.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag());
            if (linkedSource != null) {
                if (source() == null) { linkedSource = null; card.notifyUser(player,MemoryCardMessages.INVALID_MACHINE); }
                else { providing(true); card.notifyUser(player,MemoryCardMessages.SETTINGS_LOADED); }
                getHost().markForSave();
            }
        } else if (held.get(AEComponents.EXPORTED_SETTINGS_SOURCE) == null && provider) {
            providing(false); card.notifyUser(player,MemoryCardMessages.SETTINGS_CLEARED);
        }
        return true;
    }
    @Override protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if (getHost() != null && getLevel() != null && !isClientSide()) refreshSource();
    }
    @Override public void removeFromWorld() { if (!isClientSide()) closeSource(); super.removeFromWorld(); }
    @Override public TickingRequest getTickingRequest(IGridNode node) { return new TickingRequest(30,30,false); }
    @Override public TickRateModulation tickingRequest(IGridNode node, int ticks) {
        if (drainColor != 0 && System.currentTimeMillis()-lastColorUpdate > 500) color(0);
        refreshSource(); return TickRateModulation.SAME;
    }
    @Override public void readFromNBT(CompoundTag data, HolderLookup.Provider lookup) {
        super.readFromNBT(data,lookup);
        if (data.contains("uid",4)) uid = data.getLong("uid");
        provider = data.getBoolean("isProvider"); linkedSource = provider ? DigiVisSourceData.read(data.getCompound("linkedSource")) : null;
    }
    @Override public void writeToNBT(CompoundTag data, HolderLookup.Provider lookup) {
        super.writeToNBT(data,lookup); data.putLong("uid",uid);
        if (provider) { data.putBoolean("isProvider",true); if (linkedSource != null) data.put("linkedSource",linkedSource.write()); }
    }
    @Override public void writeToStream(RegistryFriendlyByteBuf data) { super.writeToStream(data); data.writeInt(drainColor); }
    @Override public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed = super.readFromStream(data); int old = drainColor; drainColor = data.readInt(); return changed || old != drainColor;
    }
    @Override public int getLightLevel() { return 8; }
    @Override public float getCableConnectionLength(AECableType cable) { return 2; }
    @Override public void getBoxes(IPartCollisionHelper boxes) { boxes.addBox(6,6,0,10,10,1); boxes.addBox(4,4,1,12,12,2); boxes.addBox(5,5,2,11,11,3); }
    @Override public IPartModel getStaticModels() { return isActive() ? ON : OFF; }
    @Override public boolean requireDynamicRender() { return true; }
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    @Override public void renderDynamic(float partialTicks, com.mojang.blaze3d.vertex.PoseStack poses,
            net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay) {
        thaumicenergistics.client.VisInterfaceRendering.render(this,poses,buffers);
    }
}
