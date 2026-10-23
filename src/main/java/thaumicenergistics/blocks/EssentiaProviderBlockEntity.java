package thaumicenergistics.blocks;

import appeng.api.config.Actionable;
import appeng.api.networking.*;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.stacks.AEKey;
import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.*;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.api.EssentiaProviderAccess;
import thaumicenergistics.storage.EssentiaKey;

/** Original all-face pipe provider; CC integrations use the read-only own API. */
public final class EssentiaProviderBlockEntity extends ProviderBlockEntity implements IEssentiaTransport,EssentiaProviderAccess,IStorageWatcherNode {
    private int tickCount,tickRate=15;
    private final List<WeakReference<Listener>> listeners=new ArrayList<>();
    private final Map<EssentiaKey,Long> watchedAmounts=new HashMap<>();
    private IStackWatcher watcher;
    private IGrid watchedGrid;
    private boolean broken;
    public EssentiaProviderBlockEntity(BlockPos pos,BlockState state){
        super(ThaumicEnergistics.ESSENTIA_PROVIDER_ENTITY.get(),pos,state,3);
        getMainNode().addService(IStorageWatcherNode.class,this);
    }
    public void serverTick(){
        if(++tickCount<tickRate)return;tickCount=0;tickRate=15;
        if(level==null)return;
        for(var side:Direction.values()){
            var pos=worldPosition.relative(side);var opposite=side.getOpposite();if(!level.hasChunkAt(pos))continue;
            var source=level.getCapability(EssentiaCapabilities.TRANSPORT,pos,opposite);
            if(source==null||!source.canOutputTo(opposite)||source.getEssentiaAmount(opposite)<=0)continue;
            if(getSuctionAmount(side)<source.getMinimumSuction()||getSuctionAmount(side)<source.getSuctionAmount(opposite))continue;
            var aspect=source.getEssentiaType(opposite);
            if(aspect!=null&&addEssentia(aspect,1,side,Actionable.SIMULATE)==1)
                addEssentia(aspect,source.takeEssentia(aspect,1,opposite),side,Actionable.MODULATE);
        }
    }
    private Aspect neighborWanted(Direction side){
        if(side==null||level==null||!level.hasChunkAt(worldPosition.relative(side)))return null;
        var neighbor=level.getCapability(EssentiaCapabilities.TRANSPORT,worldPosition.relative(side),side.getOpposite());
        return neighbor==null?null:neighbor.getSuctionType(side.getOpposite());
    }
    public int addEssentia(Aspect aspect,int amount,Direction side,Actionable mode){
        if(aspect==null||amount<=0)return 0;var network=storage();if(network==null)return 0;
        int accepted=(int)network.insert(EssentiaKey.of(aspect),amount,mode,getMachineSource());
        if(mode==Actionable.MODULATE&&accepted>0)tickRate=5;return accepted;
    }
    @Override public int addEssentia(Aspect aspect,int amount,Direction side){return addEssentia(aspect,amount,side,Actionable.MODULATE);}
    @Override public int takeEssentia(Aspect aspect,int amount,Direction side){return extractEssentiaFromNetwork(aspect,amount,false);}
    @Override public boolean isConnectable(Direction side){return true;}
    @Override public boolean canInputFrom(Direction side){return true;}
    @Override public boolean canOutputTo(Direction side){return true;}
    @Override public void setSuction(Aspect aspect,int amount){}
    @Override public Aspect getSuctionType(Direction side){return null;}
    @Override public int getSuctionAmount(Direction side){return 8;}
    @Override public int getMinimumSuction(){return 1;}
    @Override public boolean renderExtendedTube(){return false;}
    @Override public Aspect getEssentiaType(Direction side){var aspect=neighborWanted(side);return aspect!=null&&getAspectAmountInNetwork(aspect)>0?aspect:null;}
    @Override public int getEssentiaAmount(Direction side){var aspect=neighborWanted(side);return aspect==null?0:(int)Math.min(Integer.MAX_VALUE,getAspectAmountInNetwork(aspect));}
    @Override public IGrid providerGrid(){return getMainNode().getGrid();}
    @Override public Map<Aspect,Long> essentiaSnapshot(){return networkSnapshot();}
    @Override public void addProviderListener(Listener listener){
        Objects.requireNonNull(listener);listeners.removeIf(ref->ref.get()==null);
        if(listeners.stream().noneMatch(ref->ref.get()==listener))listeners.add(new WeakReference<>(listener));
        configureWatcher();
    }
    @Override public void removeProviderListener(Listener listener){listeners.removeIf(ref->ref.get()==null||ref.get()==listener);configureWatcher();}
    private List<Listener> liveListeners(){
        var result=new ArrayList<Listener>();listeners.removeIf(ref->{var value=ref.get();if(value!=null)result.add(value);return value==null;});return result;
    }
    private void configureWatcher(){
        watchedGrid=providerGrid();watchedAmounts.clear();
        if(watchedGrid!=null)for(var entry:watchedGrid.getStorageService().getCachedInventory())
            if(entry.getKey() instanceof EssentiaKey key)watchedAmounts.put(key,entry.getLongValue());
        if(watcher!=null){watcher.reset();watcher.setWatchAll(!listeners.isEmpty()&&!broken);}
    }
    @Override public void updateWatcher(IStackWatcher value){watcher=value;configureWatcher();}
    @Override protected void onProviderGridChanged(){super.onProviderGridChanged();if(watchedAmounts!=null)configureWatcher();}
    @Override public void onStackChange(AEKey key,long amount){
        if(!(key instanceof EssentiaKey essentia))return;
        var grid=providerGrid();if(grid!=watchedGrid){configureWatcher();return;}
        long previous=watchedAmounts.getOrDefault(essentia,0L);if(amount==0)watchedAmounts.remove(essentia);else watchedAmounts.put(essentia,amount);
        long delta=amount-previous;var aspect=essentia.aspect();if(delta==0||aspect==null||!isActive()||broken)return;
        for(var listener:liveListeners())listener.onEssentiaChanged(grid,aspect,delta);
    }
    @Override protected void onPowerChange(boolean online){if(listeners!=null)for(var listener:liveListeners())listener.onProviderPowerChange(online);}
    @Override public void onBreakBlock(){
        if(broken)return;broken=true;
        for(var listener:liveListeners())listener.onProviderBroken();listeners.clear();
        if(watcher!=null){watcher.reset();watcher.setWatchAll(false);}watchedAmounts.clear();watchedGrid=null;
    }
    @Override public void onReady(){broken=false;super.onReady();configureWatcher();}
    @Override public void onChunkUnloaded(){onBreakBlock();super.onChunkUnloaded();}
    @Override public void setRemoved(){onBreakBlock();super.setRemoved();}
}
