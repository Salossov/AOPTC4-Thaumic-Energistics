package thaumicenergistics.parts;

import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.util.InteractionUtil;
import appeng.util.Platform;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.EssentiaItemApi;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.storage.PortableEssentiaTransfers;
import thaumicenergistics.storage.PoweredEssentiaStorage;

/** PartEssentiaConversionMonitor, using native portable-item conversion through the public API. */
public final class EssentiaConversionMonitorPart extends EssentiaStorageMonitorPart {
    private static final IPartModel OFF=model("off"),ON=model("on"),EMPTY_OFF=model("empty_off"),EMPTY_ON=model("empty_on");
    private final ConversionClickTracker<Aspect> clicks=new ConversionClickTracker<>();
    public EssentiaConversionMonitorPart(IPartItem<?> item){super(item);}
    private static IPartModel model(String state){
        var ids=List.of(ThaumicEnergistics.id("part/essentia_conversion_monitor_"+state));
        return new IPartModel(){@Override public List<ResourceLocation> getModels(){return ids;}};
    }
    @Override public IPartModel getStaticModels(){return getDisplayed()==null?(isActive()?EMPTY_ON:EMPTY_OFF):(isActive()?ON:OFF);}
    @Override public boolean onUseItemOn(ItemStack held,Player player,InteractionHand hand,Vec3 hit){
        if(isClientSide())return true;
        if(!allowed(player))return false;
        if(player.isShiftKeyDown())return deposit(player,hand);
        if(InteractionUtil.canWrenchRotate(held)||!isLocked()||getDisplayed()==null)return super.onUseItemOn(held,player,hand,hit);
        var aspect=getDisplayed().aspect();
        if(aspect==null)return false;
        var label=EssentiaItemApi.configureLabel(held,aspect);
        if(label.isPresent()){
            player.setItemInHand(hand,label.get());sync(player);return true;
        }
        var grid=getMainNode().getGrid();
        var result=PortableEssentiaTransfers.fill(held,aspect,PoweredEssentiaStorage.of(grid),grid.getStorageService().getInventory(),IActionSource.ofPlayer(player,this));
        if(result.isEmpty())return false;
        held.shrink(1);give(player,result.get().resultStack());sync(player);return true;
    }
    private boolean deposit(Player player,InteractionHand hand){
        if(EssentiaItemApi.inspect(player.getItemInHand(hand)).isEmpty())return false;
        var batch=clicks.consume(player.getUUID(),getLevel().getServer().getTickCount());
        if(batch!=null){
            var inventory=player.getInventory();
            for(int slot=0;slot<inventory.getContainerSize();slot++){
                int count=inventory.getItem(slot).getCount();
                var deposited=drain(player,inventory.getItem(slot),batch);
                // The original revisits a stacked slot after consuming one container.
                if(deposited!=null&&inventory.getItem(slot).getCount()>0&&inventory.getItem(slot).getCount()!=count)slot--;
            }
            sync(player);return true;
        }
        var aspect=drain(player,player.getItemInHand(hand),null);
        if(aspect==null)return false;
        clicks.deposited(player.getUUID(),getLevel().getServer().getTickCount(),aspect);sync(player);return true;
    }
    private Aspect drain(Player player,ItemStack held,Aspect required){
        var contents=EssentiaItemApi.inspect(held);
        if(contents.isEmpty()||contents.get().amount()==0)return null;
        var grid=getMainNode().getGrid();
        var result=PortableEssentiaTransfers.deposit(held,required,PoweredEssentiaStorage.of(grid),grid.getStorageService().getInventory(),IActionSource.ofPlayer(player,this));
        if(result.isEmpty())return null;
        // User-approved fix: do not discard the empty result when the inventory is full.
        held.shrink(1);give(player,result.get().resultStack());
        return contents.get().aspect();
    }
    private void give(Player player,ItemStack result){
        player.getInventory().add(result);
        if(!result.isEmpty())Platform.spawnDrops(getLevel(),getBlockEntity().getBlockPos().relative(getSide()),List.of(result));
    }
    private static void sync(Player player){player.getInventory().setChanged();player.containerMenu.broadcastChanges();}
}
