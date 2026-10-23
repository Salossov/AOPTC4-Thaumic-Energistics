package thaumicenergistics.menu;

import appeng.api.config.ViewItems;
import appeng.api.networking.security.IActionSource;
import appeng.util.Platform;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.EssentiaItemApi;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.parts.EssentiaTerminalPart;
import thaumicenergistics.network.TerminalStatePayload;
import thaumicenergistics.storage.*;

public final class EssentiaTerminalMenu extends AbstractContainerMenu {
    private final Player owner;
    private final EssentiaTerminalPart part;
    private final TerminalInventory inventory;
    private int ticks;
    private long lastCursorSound=System.currentTimeMillis();
    private TerminalStatePayload state,lastSent;
    public EssentiaTerminalMenu(int id,Inventory playerInventory,EssentiaTerminalPart part){
        super(ThaumicEnergistics.TERMINAL_MENU.get(),id);owner=playerInventory.player;this.part=part;
        inventory=part==null?new TerminalInventory(()->{}):part.inventory();
        state=new TerminalStatePayload(id,0,0,"",List.of());
        addSlot(new Slot(inventory,0,8,92){@Override public boolean mayPlace(ItemStack item){return TerminalInventory.accepts(item);}});
        addSlot(new Slot(inventory,1,26,92){@Override public boolean mayPlace(ItemStack item){return false;}});
        for(int column=0;column<9;column++)addSlot(new Slot(playerInventory,column,8+column*18,180));
        for(int row=0;row<3;row++)for(int column=0;column<9;column++)addSlot(new Slot(playerInventory,9+row*9+column,8+column*18,122+row*18));
    }
    public List<TerminalEntry> entries(){return state.entries();}
    public int sort(){return state.sort();}
    public ViewItems view(){return ViewItems.values()[Math.floorMod(state.view(),ViewItems.values().length)];}
    public Aspect selected(){return Aspect.getAspect(state.selected());}
    public void receive(TerminalStatePayload value){if(part==null&&owner.level().isClientSide&&value.containerId()==containerId)state=value;}
    public static boolean partValid(EssentiaTerminalPart part,Player player){
        if(part==null||part.getHost()==null||player instanceof net.neoforged.neoforge.common.util.FakePlayer)return false;
        var tile=part.getBlockEntity();var pos=tile.getBlockPos();
        return player.level()==part.getLevel()&&player.level().hasChunkAt(pos)&&!tile.isRemoved()&&player.level().getBlockEntity(pos)==tile
                &&part.getHost().getPart(part.getSide())==part&&player.distanceToSqr(pos.getCenter())<=64&&Platform.hasPermissions(part.getHost().getLocation(),player);
    }
    @Override public boolean stillValid(Player player){return player==owner&&(part==null?player.level().isClientSide:partValid(part,player));}
    @Override public void broadcastChanges(){
        if(part!=null&&stillValid(owner)){
            var grid=part.getMainNode().getGrid();
            if(grid!=null&&++ticks>3){
                inventory.process(part.selected(),PoweredEssentiaStorage.of(grid),grid.getStorageService().getInventory(),IActionSource.ofPlayer(owner,part));ticks=0;
            }
            state=new TerminalStatePayload(containerId,part.sort(),part.view().ordinal(),part.selected()==null?"":part.selected().getTag(),part.entries());
            if(owner instanceof ServerPlayer player&&!state.equals(lastSent)){PacketDistributor.sendToPlayer(player,state);lastSent=state;}
        }
        super.broadcastChanges();
    }
    public boolean action(Player player,int menuId,int action,String tag){
        if(part==null||player.containerMenu!=this||menuId!=containerId||!stillValid(player))return false;
        var aspect=tag.isEmpty()?null:Aspect.getAspect(tag);if(!tag.isEmpty()&&aspect==null)return false;
        switch(action){
            case 0->part.selected(aspect);
            case 1->{if(aspect==null||!cursorTransfer(aspect))return false;}
            case 2,3->part.rotateSort(action==3);
            case 4,5->part.rotateView(action==5);
            case 6->{
                var grid=part.getMainNode().getGrid();
                if(aspect==null||grid==null||!(player instanceof ServerPlayer server)||!grid.getCraftingService().isCraftable(EssentiaKey.of(aspect)))return false;
                appeng.menu.me.crafting.CraftAmountMenu.open(server,appeng.menu.locator.MenuLocators.forPart(part),EssentiaKey.of(aspect),1);return true;
            }
            default->{return false;}
        }
        // State sync is independent of the work cadence: a button must not run an extra cycle.
        syncState();super.broadcastChanges();return true;
    }
    private void syncState(){
        state=new TerminalStatePayload(containerId,part.sort(),part.view().ordinal(),part.selected()==null?"":part.selected().getTag(),part.entries());
        if(owner instanceof ServerPlayer player&&!state.equals(lastSent)){PacketDistributor.sendToPlayer(player,state);lastSent=state;}
    }
    private boolean canGive(ItemStack result){
        for(int i=0;i<36;i++){var item=owner.getInventory().getItem(i);if(item.isEmpty()||ItemStack.isSameItemSameComponents(item,result)&&item.getCount()+result.getCount()<=item.getMaxStackSize())return true;}return false;
    }
    private boolean cursorTransfer(Aspect aspect){
        var held=getCarried();if(held.isEmpty())return false;
        var grid=part.getMainNode().getGrid();if(grid==null)return false;
        var powered=PoweredEssentiaStorage.of(grid);var raw=grid.getStorageService().getInventory();var source=IActionSource.ofPlayer(owner,part);var one=held.copyWithCount(1);
        var quote=TerminalInventory.quote(one,aspect,powered,source);if(quote.isEmpty()||held.getCount()>1&&!canGive(quote.get()))return false;
        var converted=TerminalInventory.convert(one,aspect,powered,raw,source);if(converted.isEmpty())return false;
        var result=converted.get().result();
        if(held.getCount()>1&&!canGive(result)){
            var old=EssentiaItemApi.inspect(one).orElseThrow();var key=EssentiaKey.of(old.amount()>0?old.aspect():aspect);
            long returned=old.amount()>0?raw.extract(key,converted.get().work(),appeng.api.config.Actionable.MODULATE,source):raw.insert(key,converted.get().work(),appeng.api.config.Actionable.MODULATE,source);
            if(returned!=converted.get().work())throw new IllegalStateException("Terminal cursor changed; rollback failed");return false;
        }
        if(held.getCount()==1)setCarried(result);else{held.shrink(1);owner.getInventory().add(result);}
        if(owner instanceof ServerPlayer server&&System.currentTimeMillis()-lastCursorSound>900){
            boolean label=EssentiaItemApi.configureLabel(result,thaumcraft.api.aspects.Aspects.AIR).isPresent();
            var sound=label?net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getOptional(net.minecraft.resources.ResourceLocation.parse("thaumcraft:page")).orElse(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN):net.minecraft.sounds.SoundEvents.PLAYER_SWIM;
            // The legacy UI sound factory and modern SimpleSoundInstance.forUI use volume .25.
            server.playNotifySound(sound,net.minecraft.sounds.SoundSource.MASTER,.25f,1);lastCursorSound=System.currentTimeMillis();
        }
        return true;
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player){if(stillValid(player))super.clicked(slot,button,type,player);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(!stillValid(player)||index<0||index>=slots.size())return ItemStack.EMPTY;
        var slot=getSlot(index);if(!slot.hasItem())return ItemStack.EMPTY;var item=slot.getItem();boolean moved;
        if(index<2)moved=moveItemStackTo(item,11,38,false); // Original output/input shift-click targets main inventory only.
        else if(TerminalInventory.accepts(item))moved=moveItemStackTo(item,0,1,false);
        else moved=moveItemStackTo(item,index<11?11:2,index<11?38:11,false);
        if(moved){if(item.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();}
        return ItemStack.EMPTY;
    }
}
