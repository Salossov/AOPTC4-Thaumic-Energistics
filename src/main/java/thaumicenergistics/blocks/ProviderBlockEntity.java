package thaumicenergistics.blocks;

import appeng.api.config.Actionable;
import appeng.api.implementations.blockentities.IColorableBlockEntity;
import appeng.api.networking.*;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartHost;
import appeng.api.stacks.KeyCounter;
import appeng.api.util.*;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.storage.*;

/** TileProviderBase: one channel, powered transfers and cable-selected/forced color. */
public abstract class ProviderBlockEntity extends AENetworkedBlockEntity implements IColorableBlockEntity {
    private AEColor color=AEColor.TRANSPARENT;
    private Direction attachment;
    private boolean forcedColor,active,checkingColor;
    private final IActionSource source=IActionSource.ofMachine(this);

    protected ProviderBlockEntity(BlockEntityType<?> type,BlockPos pos,BlockState state,double idlePower){
        super(type,pos,state);
        getMainNode().setFlags(GridFlags.REQUIRE_CHANNEL).setIdlePowerUsage(idlePower);
    }
    @Override protected IManagedGridNode createMainNode(){
        return GridHelper.createManagedNode(this,new IGridNodeListener<ProviderBlockEntity>(){
            @Override public void onSaveChanges(ProviderBlockEntity owner,IGridNode node){owner.saveChanges();}
            @Override public void onStateChanged(ProviderBlockEntity owner,IGridNode node,State reason){owner.onMainNodeStateChanged(reason);}
            @Override public void onInWorldConnectionChanged(ProviderBlockEntity owner,IGridNode node){owner.checkGridConnectionColor();}
            @Override public void onGridChanged(ProviderBlockEntity owner,IGridNode node){owner.onProviderGridChanged();}
        });
    }
    @Override public void onMainNodeStateChanged(IGridNodeListener.State reason){
        checkGridConnectionColor();refreshActive();
        if(level!=null&&!level.isClientSide)markForUpdate();
    }
    protected void onProviderGridChanged(){checkGridConnectionColor();refreshActive();}
    protected void onPowerChange(boolean powered){}
    public boolean isActive(){
        if(level!=null&&!level.isClientSide)refreshActive();
        return active;
    }
    private void refreshActive(){
        if(level==null||level.isClientSide||getMainNode().getNode()==null)return;
        boolean next=getMainNode().isActive();
        if(next!=active){active=next;onPowerChange(next);markForUpdate();}
    }
    public void attachment(Direction side){attachment=side;setChanged();checkGridConnectionColor();}
    public Direction attachment(){return attachment;}
    public boolean isColorForced(){return forcedColor;}
    @Override public AEColor getColor(){return color;}
    public AEColor getGridColor(){return color;}
    private AEColor cableColor(Direction side){
        if(side==null||!(level instanceof net.minecraft.server.level.ServerLevel server))return null;
        var pos=worldPosition.relative(side);
        var chunk=server.getChunkSource().getChunkNow(pos.getX()>>4,pos.getZ()>>4);
        if(chunk==null||!chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL))return null;
        // Only cable buses influence the original automatic color selection.
        return chunk.getBlockEntity(pos,net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK) instanceof IPartHost host?host.getColor():null;
    }
    public void checkGridConnectionColor(){
        if(level==null||level.isClientSide||forcedColor||checkingColor)return;
        // AE2 destroys neighbouring nodes while LevelChunk is unloading, before
        // every block entity is marked removed. Recolouring here would rescan
        // capabilities and acquire new generation claims for the dying chunk.
        if(isRemoved()||!(level instanceof net.minecraft.server.level.ServerLevel server))return;
        var chunk=server.getChunkSource().getChunkNow(worldPosition.getX()>>4,worldPosition.getZ()>>4);
        if(chunk==null||!chunk.getFullStatus().isOrAfter(net.minecraft.server.level.FullChunkStatus.FULL)
                ||chunk.getBlockEntity(worldPosition,net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK)!=this)return;
        checkingColor=true;
        try{
            var attached=cableColor(attachment);
            if(attached!=null){setProviderColor(attached);return;}
            for(var side:Direction.values()){
                var neighbor=cableColor(side);if(neighbor==null)continue;
                if(neighbor==color){attachment=side;setChanged();return;}
                if(color==AEColor.TRANSPARENT){attachment=side;setProviderColor(neighbor);return;}
            }
            attachment=null;setProviderColor(AEColor.TRANSPARENT);
        }finally{checkingColor=false;}
    }
    private void setProviderColor(AEColor value){
        color=value;getMainNode().setGridColor(value);
        if(level!=null&&!level.isClientSide){markForUpdate();setChanged();}
    }
    @Override public boolean recolourBlock(Direction side,AEColor value,Player player){
        if(level==null||level.isClientSide||player!=null&&!valid(player))return false;
        forcedColor=true;setProviderColor(Objects.requireNonNull(value));return true;
    }
    public boolean valid(Player player){
        return !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)&&level==player.level()
                &&level.hasChunkAt(worldPosition)&&!isRemoved()&&level.getBlockEntity(worldPosition)==this
                &&player.distanceToSqr(worldPosition.getCenter())<=64
                &&appeng.util.Platform.hasPermissions(new DimensionalBlockPos(this),player);
    }
    public IActionSource getMachineSource(){return source;}
    protected PoweredEssentiaStorage storage(){var grid=getMainNode().getGrid();return grid==null?null:PoweredEssentiaStorage.of(grid);}
    public long getAspectAmountInNetwork(Aspect aspect){
        var grid=getMainNode().getGrid();
        if(aspect==null||grid==null||!grid.getEnergyService().isNetworkPowered())return 0;
        return grid.getStorageService().getCachedInventory().get(EssentiaKey.of(aspect));
    }
    protected int extractEssentiaFromNetwork(Aspect aspect,int requested,boolean exact){
        if(aspect==null||requested<=0)return 0;var network=storage();if(network==null)return 0;
        var key=EssentiaKey.of(aspect);long amount=network.extract(key,requested,Actionable.SIMULATE,source);
        if(amount==0||exact&&amount!=requested)return 0;
        // Quirk kept: TileProviderBase returns its simulated amount and does not
        // replace it with the second call's result. No independent channel gate
        // was present in this path; the powered monitor enforces transfer cost.
        network.extract(key,requested,Actionable.MODULATE,source);return (int)amount;
    }
    protected Map<Aspect,Long> networkSnapshot(){
        var grid=getMainNode().getGrid();
        if(grid==null||!grid.getEnergyService().isNetworkPowered())return Map.of();
        var result=new LinkedHashMap<Aspect,Long>();
        for(var entry:grid.getStorageService().getCachedInventory())
            if(entry.getKey() instanceof EssentiaKey key&&key.aspect()!=null&&entry.getLongValue()>0)result.put(key.aspect(),entry.getLongValue());
        return Collections.unmodifiableMap(result);
    }
    @Override public AECableType getCableConnectionType(Direction side){return AECableType.SMART;}
    @Override public void onReady(){super.onReady();checkGridConnectionColor();refreshActive();}
    public void onBreakBlock(){}
    @Override public void saveAdditional(CompoundTag data,HolderLookup.Provider registries){
        super.saveAdditional(data,registries);data.putString("TEColor",color.getSerializedName());data.putInt("TEAttachSide",attachment==null?6:attachment.ordinal());data.putBoolean("ColorForced",forcedColor);
    }
    @Override public void loadTag(CompoundTag data,HolderLookup.Provider registries){
        super.loadTag(data,registries);forcedColor=data.getBoolean("ColorForced");
        color=Arrays.stream(AEColor.values()).filter(value->value.getSerializedName().equals(data.getString("TEColor"))).findFirst().orElse(AEColor.TRANSPARENT);
        int side=data.contains("TEAttachSide")?data.getInt("TEAttachSide"):6;attachment=side>=0&&side<6?Direction.values()[side]:null;
        getMainNode().setGridColor(color);
    }
    @Override protected void writeToStream(RegistryFriendlyByteBuf buffer){super.writeToStream(buffer);buffer.writeEnum(color);buffer.writeBoolean(isActive());}
    @Override protected boolean readFromStream(RegistryFriendlyByteBuf buffer){super.readFromStream(buffer);color=buffer.readEnum(AEColor.class);active=buffer.readBoolean();return true;}
}
