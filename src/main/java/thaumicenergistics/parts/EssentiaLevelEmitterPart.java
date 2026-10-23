package thaumicenergistics.parts;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.parts.IPartCollisionHelper;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.stacks.AEKey;
import appeng.api.util.AECableType;
import appeng.parts.AEBasePart;
import appeng.util.SettingsFrom;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.EssentiaLevelEmitterMenu;
import thaumicenergistics.storage.EssentiaKey;

public final class EssentiaLevelEmitterPart extends AEBasePart implements IStorageWatcherNode {
    public static final long MAX_THRESHOLD = 9999999999L;
    private static final IPartModel OFF = model("off"), ON = model("on");
    private static IPartModel model(String state) {
        var ids=List.of(ThaumicEnergistics.id("part/essentia_level_emitter_"+state));
        return new IPartModel() { @Override public List<ResourceLocation> getModels() { return ids; } };
    }
    private EssentiaKey filter;
    private IStackWatcher watcher;
    private long threshold, currentAmount;
    private boolean lowSignal, emitting;

    public EssentiaLevelEmitterPart(IPartItem<?> item) {
        super(item);
        getMainNode().setIdlePowerUsage(.3).setFlags(GridFlags.REQUIRE_CHANNEL).addService(IStorageWatcherNode.class,this);
    }
    public EssentiaKey filter() { return filter; }
    public long threshold() { return threshold; }
    public long currentAmount() { return currentAmount; }
    public boolean lowSignal() { return lowSignal; }
    public boolean emitting() { return emitting; }
    private void serverMutation() {
        if(getLevel()==null||isClientSide()||!getLevel().getServer().isSameThread())throw new IllegalStateException("Server thread required");
    }
    public void filter(EssentiaKey value) {
        serverMutation();
        if(java.util.Objects.equals(filter,value))return;
        filter=value;getHost().markForSave();configureWatcher();
    }
    public void threshold(long value) {
        serverMutation();threshold=Math.clamp(value,0,MAX_THRESHOLD);getHost().markForSave();updateEmitting();
    }
    public void lowSignal(boolean value) { serverMutation();lowSignal=value;getHost().markForSave();updateEmitting(); }
    private void configureWatcher() {
        if(watcher!=null) {watcher.reset();if(filter!=null)watcher.add(filter);}
        var grid=getMainNode().getGrid();
        currentAmount=filter==null||grid==null||watcher==null?0:grid.getStorageService().getCachedInventory().get(filter);
        updateEmitting();
    }
    @Override public void updateWatcher(IStackWatcher value) { watcher=value;configureWatcher(); }
    @Override public void onStackChange(AEKey key,long amount) {
        if(filter!=null&&filter.equals(key)) {currentAmount=amount;updateEmitting();}
    }
    private void updateEmitting() {
        if(getHost()==null||!getMainNode().isActive())return;
        boolean next=lowSignal?currentAmount<threshold:currentAmount>=threshold;
        if(next==emitting)return;
        emitting=next;getHost().markForSave();getHost().markForUpdate();
        getHost().notifyNeighbors();getHost().notifyNeighborNow(getSide());
    }
    @Override protected void onMainNodeStateChanged(IGridNodeListener.State reason) {
        super.onMainNodeStateChanged(reason);
        if(getHost()!=null&&!isClientSide())configureWatcher();
    }
    @Override public boolean onUseWithoutItem(Player player,Vec3 hit) {
        if(player instanceof net.minecraft.server.level.ServerPlayer server&&EssentiaLevelEmitterMenu.partValid(this,player))
            server.openMenu(new net.minecraft.world.SimpleMenuProvider((id,inventory,owner)->new EssentiaLevelEmitterMenu(id,inventory,this),getName()));
        return true;
    }
    @Override public boolean canConnectRedstone() { return true; }
    @Override public int isProvidingStrongPower() { return emitting?15:0; }
    @Override public int isProvidingWeakPower() { return isProvidingStrongPower(); }
    @Override public int getLightLevel() { return emitting?7:0; }
    @Override public float getCableConnectionLength(AECableType cable) { return 8; }
    @Override public void getBoxes(IPartCollisionHelper boxes) { boxes.addBox(7,7,11,9,9,15); }
    @Override public IPartModel getStaticModels() { return emitting?ON:OFF; }
    @Override public void animateTick(net.minecraft.world.level.Level level,net.minecraft.core.BlockPos pos,net.minecraft.util.RandomSource random) {
        if(!emitting)return;
        var side=getSide();
        level.addParticle(net.minecraft.core.particles.DustParticleOptions.REDSTONE,
                pos.getX()+.5+side.getStepX()*.45+(random.nextFloat()-.5)*.2,
                pos.getY()+.5+side.getStepY()*.45+(random.nextFloat()-.5)*.2,
                pos.getZ()+.5+side.getStepZ()*.45+(random.nextFloat()-.5)*.2,0,0,0);
    }
    private void settings(CompoundTag data,boolean world) {
        if(filter!=null)data.putString("aspect",filter.tag());
        if(world||filter!=null) {if(lowSignal)data.putInt("mode",appeng.api.config.RedstoneMode.LOW_SIGNAL.ordinal());if(threshold>0)data.putLong("wantedAmount",threshold);}
        if(world&&emitting)data.putBoolean("emitting",true);
    }
    // Original stores legacy AE2 RedstoneMode ordinals; HIGH_SIGNAL is the omitted default.
    private static boolean lowMode(CompoundTag data) { return data.getInt("mode")==appeng.api.config.RedstoneMode.LOW_SIGNAL.ordinal(); }
    @Override public void writeToNBT(CompoundTag data,HolderLookup.Provider registries) { super.writeToNBT(data,registries);settings(data,true); }
    @Override public void readFromNBT(CompoundTag data,HolderLookup.Provider registries) {
        super.readFromNBT(data,registries);filter=data.getString("aspect").isEmpty()?null:EssentiaKey.ofTag(data.getString("aspect"));
        lowSignal=lowMode(data);threshold=data.getLong("wantedAmount");emitting=data.getBoolean("emitting");
    }
    @Override public void exportSettings(SettingsFrom mode,DataComponentMap.Builder builder) {
        super.exportSettings(mode,builder);var data=new CompoundTag();settings(data,false);builder.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
    }
    @Override public void importSettings(SettingsFrom mode,DataComponentMap components,Player player) {
        serverMutation();super.importSettings(mode,components,player);
        var data=components.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag();
        filter=data.getString("aspect").isEmpty()?null:EssentiaKey.ofTag(data.getString("aspect"));
        lowSignal=lowMode(data);threshold=data.getLong("wantedAmount");getHost().markForSave();configureWatcher();
    }
    @Override public void writeToStream(RegistryFriendlyByteBuf data) { super.writeToStream(data);data.writeBoolean(emitting); }
    @Override public boolean readFromStream(RegistryFriendlyByteBuf data) {
        boolean changed=super.readFromStream(data),old=emitting;emitting=data.readBoolean();return changed||old!=emitting;
    }
}
