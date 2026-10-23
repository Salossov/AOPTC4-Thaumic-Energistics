package thaumicenergistics.menu;

import appeng.api.config.*;
import appeng.api.implementations.blockentities.IWirelessAccessPoint;
import appeng.api.networking.*;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.storage.*;
import appeng.core.AEConfig;
import appeng.helpers.WirelessTerminalMenuHost;
import appeng.menu.locator.*;
import appeng.util.Platform;
import java.util.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.items.WirelessEssentiaTerminalItem;
import thaumicenergistics.storage.*;

/** Native locator/crafting bridge with ThE's AP selection, item energy and menu cadence. */
public final class WirelessTerminalHost extends WirelessTerminalMenuHost<WirelessEssentiaTerminalItem> implements TerminalHost {
    private final ItemStack bound;
    private final TerminalInventory work=new TerminalInventory(()->{});
    private IGrid grid;
    private IWirelessAccessPoint accessPoint;
    private Aspect selected;
    private int powerTicks=1;
    private double distanceMultiplier=1;
    public WirelessTerminalHost(WirelessEssentiaTerminalItem item,Player player,ItemMenuHostLocator locator){
        super(item,player,locator,(owner,submenu)->item.openFromInventory(owner,locator));bound=getItemStack();refreshConnection();
    }
    // The superclass invokes these from its constructor. Its native ticking/idle
    // buffering would change ThE's numbers and cadence, so the adapter owns both.
    @Override protected void updateConnectedAccessPoint(){}
    @Override protected void updateLinkStatus(){}
    @Override public void tick(){}
    @Override protected double getPowerDrainPerTick(){return 0;}
    @Override public boolean consumeIdlePower(Actionable mode){return true;}
    private boolean boundPresent(){return bound!=null&&getItemStack()==bound&&bound.is(getItem())&&!bound.isEmpty();}
    private int squaredDistance(IWirelessAccessPoint point){
        var pos=point.getLocation().getPos();var player=getPlayer();
        int dx=pos.getX()-(int)player.getX(),dy=pos.getY()-(int)player.getY(),dz=pos.getZ()-(int)player.getZ();
        return dx*dx+dy*dy+dz*dz; // Original truncation and int arithmetic.
    }
    private boolean usable(IWirelessAccessPoint point){
        if(point==null||point.getGrid()!=grid||!point.isActive())return false;
        var location=point.getLocation();var level=getPlayer().level();
        if(!location.isInWorld(level)||!level.hasChunkAt(location.getPos())||level.getBlockEntity(location.getPos())!=point)return false;
        return squaredDistance(point)<=point.getRange()*point.getRange()&&Platform.hasPermissions(location,getPlayer());
    }
    private boolean refreshConnection(){
        if(!boundPresent()||isClientSide()||getPlayer() instanceof net.neoforged.neoforge.common.util.FakePlayer||getItem().getAECurrentPower(bound)<=0){accessPoint=null;return false;}
        grid=getItem().getLinkedGrid(bound,getPlayer().level(),null);
        if(grid==null){accessPoint=null;return false;}
        if(usable(accessPoint))return true;
        accessPoint=null;int nearest=Integer.MAX_VALUE;
        // Approved initial-search fix: search even when there was no previous AP.
        for(var node:grid.getNodes())if(node.getOwner() instanceof IWirelessAccessPoint candidate&&usable(candidate)){
            int distance=squaredDistance(candidate);if(distance<nearest){nearest=distance;accessPoint=candidate;}
        }
        return accessPoint!=null;
    }
    @Override public boolean valid(Player player){return player==getPlayer()&&refreshConnection();}
    @Override public boolean isValid(){return valid(getPlayer());}
    @Override public ILinkStatus getLinkStatus(){return refreshConnection()?ILinkStatus.ofConnected():ILinkStatus.ofDisconnected();}
    @Override public IGridNode getActionableNode(){return accessPoint==null?null:accessPoint.getActionableNode();}
    @Override public TerminalInventory inventory(){return work;}
    @Override public MEStorage rawStorage(){return refreshConnection()?grid.getStorageService().getInventory():null;}
    @Override public MEStorage getInventory(){var raw=rawStorage();return raw==null?appeng.me.storage.NullInventory.of():raw;}
    @Override public IEnergySource transferEnergy(){return this;}
    @Override public MEStorage poweredStorage(){var raw=rawStorage();return raw==null?null:new WirelessEssentiaStorage(raw,this);}
    @Override public double extractAEPower(double amount,Actionable mode,PowerMultiplier ignored){
        if(WirelessEssentiaTerminalItem.GLOBAL_POWER_MULTIPLIER==0)return amount;
        double adjusted=amount*distanceMultiplier;
        double extracted=mode==Actionable.SIMULATE?Math.min(getItem().getAECurrentPower(bound),adjusted):getItem().extractAEPower(bound,adjusted,mode);
        return extracted/distanceMultiplier;
    }
    @Override public void workTick(int elapsed){
        powerTicks+=elapsed;
        if(powerTicks>10){
            if(accessPoint!=null)distanceMultiplier=AEConfig.instance().wireless_getDrainRate(Math.sqrt(squaredDistance(accessPoint)));
            extractAEPower(powerTicks,Actionable.MODULATE,PowerMultiplier.CONFIG);getPlayer().getInventory().setChanged();powerTicks=0;
        }
    }
    public double distanceMultiplier(){return distanceMultiplier;}
    public IWirelessAccessPoint connectedAccessPoint(){return accessPoint;}
    private TerminalSettings settings(){return bound.getOrDefault(ThaumicEnergistics.TERMINAL_SETTINGS,TerminalSettings.DEFAULT);}
    private void settings(TerminalSettings value){bound.set(ThaumicEnergistics.TERMINAL_SETTINGS,value);getPlayer().getInventory().setChanged();}
    @Override public int sort(){return settings().sort();}
    @Override public ViewItems view(){return ViewItems.values()[settings().view()];}
    @Override public void rotateSort(boolean backwards){settings(new TerminalSettings(1-sort(),view().ordinal()));}
    @Override public void rotateView(boolean backwards){var values=ViewItems.values();settings(new TerminalSettings(sort(),(view().ordinal()+(backwards?values.length-1:1))%values.length));}
    @Override public Aspect selected(){return selected;}
    @Override public void selected(Aspect value){selected=value;}
    @Override public int lockedPlayerSlot(){var slot=getPlayerInventorySlot();return slot==null?-1:slot;}
    @Override public boolean supportsCrafting(){return true;}
    @Override public MenuHostLocator craftingLocator(){return getLocator();}
    @Override public List<TerminalEntry> entries(){
        if(rawStorage()==null)return List.of();var result=new LinkedHashMap<EssentiaKey,TerminalEntry>();
        for(var entry:grid.getStorageService().getCachedInventory())if(entry.getKey() instanceof EssentiaKey key&&entry.getLongValue()>0)result.put(key,new TerminalEntry(key,entry.getLongValue(),false));
        for(var craftable:grid.getCraftingService().getCraftables(key->key instanceof EssentiaKey))if(craftable instanceof EssentiaKey key)result.put(key,new TerminalEntry(key,result.containsKey(key)?result.get(key).amount():0,true));
        return List.copyOf(result.values());
    }
    @Override public void removed(Player player){if(!player.level().isClientSide)for(int slot=0;slot<2;slot++){var stack=work.removeItemNoUpdate(slot);if(!stack.isEmpty())player.drop(stack,false);}}
    @Override public ItemStack getMainMenuIcon(){return bound.copy();}
    @Override public void returnToMainMenu(Player player,appeng.menu.ISubMenu submenu){getItem().openFromInventory(player,getLocator());}
}
