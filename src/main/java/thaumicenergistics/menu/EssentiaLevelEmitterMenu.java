package thaumicenergistics.menu;

import appeng.api.stacks.GenericStack;
import appeng.util.Platform;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.parts.EssentiaLevelEmitterPart;
import thaumicenergistics.storage.EssentiaKey;

public final class EssentiaLevelEmitterMenu extends AbstractContainerMenu {
    private static final int[] DELTAS={1,10,100,-1,-10,-100};
    private final Player owner;
    private final EssentiaLevelEmitterPart part;
    private final int[] words=new int[4];
    public EssentiaLevelEmitterMenu(int id,Inventory inventory,EssentiaLevelEmitterPart part) {
        super(ThaumicEnergistics.EMITTER_MENU.get(),id);owner=inventory.player;this.part=part;
        addSlot(new Slot(new SimpleContainer(1),0,124,40) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
            @Override public boolean mayPickup(Player player) { return false; }
        });
        for(int column=0;column<9;column++)addSlot(new Slot(inventory,column,8+column*18,160));
        for(int row=0;row<3;row++)for(int column=0;column<9;column++)addSlot(new Slot(inventory,9+row*9+column,8+column*18,102+row*18));
        addDataSlots(new ContainerData() {
            public int get(int index) { return words[index]; }
            public void set(int index,int value) { words[index]=value&0xffff; }
            public int getCount() { return 4; }
        });refresh();
    }
    public long threshold() { return (words[0]&0xffffL)|((words[1]&0xffffL)<<16)|((words[2]&0xffffL)<<32); }
    public boolean lowSignal() { return words[3]!=0; }
    public EssentiaKey filter() {
        var stack=GenericStack.fromItemStack(getSlot(0).getItem());return stack!=null&&stack.what() instanceof EssentiaKey key?key:null;
    }
    private void refresh() {
        if(part==null)return;
        long threshold=part.threshold();for(int index=0;index<3;index++)words[index]=(int)(threshold>>>(index*16))&0xffff;
        words[3]=part.lowSignal()?1:0;
        getSlot(0).set(part.filter()==null?ItemStack.EMPTY:GenericStack.wrapInItemStack(part.filter(),1));
    }
    @Override public void broadcastChanges() { refresh();super.broadcastChanges(); }
    public static boolean partValid(EssentiaLevelEmitterPart part,Player player) {
        if(part==null||part.getHost()==null)return false;
        var tile=part.getBlockEntity();var pos=tile.getBlockPos();
        return player.level()==part.getLevel()&&player.level().hasChunkAt(pos)&&!tile.isRemoved()
                &&player.level().getBlockEntity(pos)==tile&&part.getHost().getPart(part.getSide())==part
                &&player.distanceToSqr(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5)<=64&&Platform.hasPermissions(part.getHost().getLocation(),player);
    }
    @Override public boolean stillValid(Player player) { return player==owner&&(part==null?player.level().isClientSide:partValid(part,player)); }
    public boolean setThreshold(Player player,int menuId,long value) {
        if(part==null||player.containerMenu!=this||menuId!=containerId||!stillValid(player))return false;
        part.threshold(value);broadcastChanges();return true;
    }
    @Override public boolean clickMenuButton(Player player,int action) {
        if(part==null||!stillValid(player))return false;
        if(action==6)part.lowSignal(!part.lowSignal());
        else if(action>=0&&action<6)part.threshold(part.threshold()+DELTAS[action]);
        else if(action>=7&&action<13)part.threshold(part.threshold()+DELTAS[action-7]*100L);
        else return false;
        broadcastChanges();return true;
    }
    @Override public void clicked(int slot,int button,ClickType type,Player player) {
        if(!stillValid(player))return;
        if(slot==0) {if(part!=null&&type==ClickType.PICKUP&&(button==0||button==1)){part.filter(EssentiaBusMenu.aspectFromItem(getCarried()));broadcastChanges();}return;}
        super.clicked(slot,button,type,player);
    }
    @Override public ItemStack quickMoveStack(Player player,int index) {
        if(part==null||!stillValid(player)||index<1||index>=slots.size())return ItemStack.EMPTY;
        var slot=getSlot(index);if(!slot.hasItem())return ItemStack.EMPTY;
        var key=EssentiaBusMenu.aspectFromItem(slot.getItem());
        if(key!=null){part.filter(key);broadcastChanges();return ItemStack.EMPTY;}
        var stack=slot.getItem();var before=stack.copy();
        if(!moveItemStackTo(stack,index<10?10:1,index<10?37:10,false))return ItemStack.EMPTY;
        if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();return before;
    }
}
