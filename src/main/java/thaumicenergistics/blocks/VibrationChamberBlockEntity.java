package thaumicenergistics.blocks;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.*;
import appeng.api.util.AECableType;
import appeng.blockentity.grid.AENetworkedBlockEntity;
import appeng.util.Platform;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.*;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.VibrationChamberMenu;
import thaumicenergistics.storage.*;

public final class VibrationChamberBlockEntity extends AENetworkedBlockEntity implements IGridTickable,IEssentiaTransport,IAspectContainer {
    private ChamberContents stored=ChamberContents.EMPTY;
    private final VibrationBurnState burn=new VibrationBurnState();
    private final Set<VibrationChamberMenu> listeners=new HashSet<>();
    private static int coalBurnTime;
    public VibrationChamberBlockEntity(BlockPos pos,BlockState state){
        super(ThaumicEnergistics.VIBRATION_ENTITY.get(),pos,state);getMainNode().setIdlePowerUsage(0).addService(IGridTickable.class,this);
    }
    public ChamberContents stored(){return stored;}
    public VibrationBurnState burn(){return burn;}
    public void restoreItem(ChamberContents contents){stored=contents;changed();}
    public void addListener(VibrationChamberMenu menu){listeners.add(menu);}
    public void removeListener(VibrationChamberMenu menu){listeners.remove(menu);}
    public boolean valid(Player player){return !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)&&level==player.level()&&level.hasChunkAt(worldPosition)&&!isRemoved()&&level.getBlockEntity(worldPosition)==this&&player.distanceToSqr(worldPosition.getCenter())<=64&&Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(this),player);}
    private void changed(){setChanged();if(level!=null&&!level.isClientSide)markForUpdate();}
    public int add(Aspect aspect,int amount,Actionable mode){
        if(!thaumicenergistics.items.VibrationChamberItem.accepts(aspect)||amount<=0||stored.amount()>0&&stored.aspect()!=aspect)return 0;
        int accepted=Math.min(amount,64-stored.amount());
        if(accepted>0&&mode==Actionable.MODULATE){stored=new ChamberContents(aspect.getTag(),stored.amount()+accepted);changed();}
        return accepted;
    }
    @Override public TickingRequest getTickingRequest(IGridNode node){return new TickingRequest(10,40,false);}
    @Override public TickRateModulation tickingRequest(IGridNode node,int elapsed){
        var previous=burn.aspect();var grid=getMainNode().getGrid();TickRateModulation rate=TickRateModulation.IDLE;
        if(burn.remaining()>0){rate=burn.process(elapsed,grid==null?null:grid.getEnergyService()::injectPower);setChanged();}
        else burn.idle();
        if(stored.amount()>0&&burn.remaining()==0){
            if(coalBurnTime==0)coalBurnTime=new ItemStack(Items.COAL).getBurnTime(RecipeType.SMELTING);
            if(burn.start(stored.aspect(),coalBurnTime)){stored=new ChamberContents(stored.tag(),stored.amount()-1);changed();rate=TickRateModulation.URGENT;}
        }
        if(stored.amount()<64)pullNeighbors();
        if(previous!=burn.aspect()){
            int visual=burn.aspect()==Aspects.FIRE?1:burn.aspect()==Aspects.ENERGY?2:0;
            if(level!=null&&getBlockState().getValue(VibrationChamberBlock.BURNING)!=visual)level.setBlock(worldPosition,getBlockState().setValue(VibrationChamberBlock.BURNING,visual),3);
            changed();
        }
        if(!listeners.isEmpty()){burn.clampSpeed();for(var menu:List.copyOf(listeners))menu.onChamberUpdate();}
        return rate;
    }
    private void pullNeighbors(){
        if(level==null)return;
        for(var side:Direction.values()){
            if(!canInputFrom(side)||!level.hasChunkAt(worldPosition.relative(side)))continue;
            var source=level.getCapability(EssentiaCapabilities.TRANSPORT,worldPosition.relative(side),side.getOpposite());var opposite=side.getOpposite();
            if(source==null||!source.canOutputTo(opposite)||source.getEssentiaAmount(opposite)<=0)continue;
            int suction=getSuctionAmount(side);if(suction<source.getMinimumSuction()||suction<source.getSuctionAmount(opposite))continue;
            var aspect=source.getEssentiaType(opposite);
            if(aspect!=null&&add(aspect,1,Actionable.SIMULATE)==1)add(aspect,source.takeEssentia(aspect,1,opposite),Actionable.MODULATE);
        }
    }
    @Override public AECableType getCableConnectionType(Direction side){return AECableType.COVERED;}
    @Override public void saveAdditional(CompoundTag data,HolderLookup.Provider registries){
        super.saveAdditional(data,registries);if(stored.amount()>0){var contents=new CompoundTag();contents.putString("aspect",stored.tag());contents.putInt("amount",stored.amount());data.put("StoredEssentia",contents);}burn.save(data);
    }
    @Override public void loadTag(CompoundTag data,HolderLookup.Provider registries){
        super.loadTag(data,registries);var contents=data.getCompound("StoredEssentia");int amount=contents.getInt("amount");String tag=contents.getString("aspect");stored=amount>0&&!tag.isEmpty()?new ChamberContents(tag,Math.min(64,amount)):ChamberContents.EMPTY;burn.load(data);
    }
    @Override protected void writeToStream(RegistryFriendlyByteBuf buffer){super.writeToStream(buffer);buffer.writeUtf(stored.tag());buffer.writeVarInt(stored.amount());}
    @Override protected boolean readFromStream(RegistryFriendlyByteBuf buffer){super.readFromStream(buffer);stored=new ChamberContents(buffer.readUtf(256),buffer.readVarInt());return true;}
    @Override public boolean isConnectable(Direction side){return side!=getFront();}
    @Override public boolean canInputFrom(Direction side){return isConnectable(side);}
    @Override public boolean canOutputTo(Direction side){return false;}
    @Override public void setSuction(Aspect aspect,int amount){}
    @Override public Aspect getSuctionType(Direction side){return stored.amount()>0?stored.aspect():(level!=null&&level.getServer()!=null&&level.getServer().getTickCount()%200>100?Aspects.ENERGY:Aspects.FIRE);}
    @Override public int getSuctionAmount(Direction side){return stored.amount()==64?0:(int)(128F*(1F-stored.amount()*.75F*.015625F));}
    @Override public int takeEssentia(Aspect aspect,int amount,Direction side){return 0;}
    @Override public int addEssentia(Aspect aspect,int amount,Direction side){return add(aspect,amount,Actionable.MODULATE);}
    @Override public Aspect getEssentiaType(Direction side){return stored.amount()>0?stored.aspect():null;}
    @Override public int getEssentiaAmount(Direction side){return stored.amount();}
    @Override public int getMinimumSuction(){return 0;}
    @Override public boolean renderExtendedTube(){return false;}
    @Override public AspectList getAspects(){return stored.amount()>0&&stored.aspect()!=null?new AspectList().add(stored.aspect(),stored.amount()):new AspectList();}
    @Override public void setAspects(AspectList aspects){}
    @Override public boolean doesContainerAccept(Aspect aspect){return thaumicenergistics.items.VibrationChamberItem.accepts(aspect)&&(stored.amount()==0||stored.aspect()==aspect);}
    @Override public int addToContainer(Aspect aspect,int amount){return amount<=0?0:amount-add(aspect,amount,Actionable.MODULATE);}
    @Override public boolean takeFromContainer(Aspect aspect,int amount){return false;}
    @Override public boolean doesContainerContainAmount(Aspect aspect,int amount){return stored.aspect()==aspect&&stored.amount()>=amount;}
    @Override public int containerContains(Aspect aspect){return stored.aspect()==aspect?stored.amount():0;}
}
