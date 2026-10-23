package thaumicenergistics.assembler;

import appeng.api.upgrades.Upgrades;
import appeng.items.tools.NetworkToolItem;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.IVisDiscountGear;

/** Source slot order, real upgrades/tool storage, and inaccessible pattern/target display slots. */
public final class ArcaneAssemblerMenu extends AbstractContainerMenu {
    private final Player owner;
    private final AssemblerHost host;
    private final Container inventory;
    private final int toolSlot,firstTool,firstCore,firstPattern,firstUpgrade,target,firstArmor;
    private final ItemStack tool;
    private AssemblerStatePayload state,lastSent;
    private int displayTicks;
    public ArcaneAssemblerMenu(int id,Inventory player,FriendlyByteBuf data){this(id,player,null,data.readVarInt());}
    public ArcaneAssemblerMenu(int id,Inventory player,AssemblerHost host,int toolSlot){
        super(AssemblerRegistration.MENU.get(),id);owner=player.player;this.host=host;
        this.toolSlot=toolSlot>=0&&toolSlot<player.getContainerSize()&&player.getItem(toolSlot).getItem() instanceof NetworkToolItem?toolSlot:-1;
        tool=this.toolSlot<0?ItemStack.EMPTY:player.getItem(this.toolSlot);state=AssemblerStatePayload.empty(id);
        inventory=host==null?new SimpleContainer(AssemblerRules.SLOTS):host.inventory();
        for(int col=0;col<9;col++)addSlot(new Slot(player,col,8+col*18,173));
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)addSlot(new Slot(player,9+row*9+col,8+col*18,115+row*18));
        firstTool=slots.size();
        if(hasNetworkTool()){
            var toolInventory=NetworkToolItem.getInventory(tool).toContainer();
            for(int column=0;column<3;column++)for(int row=0;row<3;row++)addSlot(new Slot(toolInventory,column+row*3,187+column*18,137+row*18){
                @Override public boolean mayPlace(ItemStack stack){return toolInventory.canPlaceItem(getContainerSlot(),stack);}
            });
        }
        firstCore=slots.size();addSlot(new Slot(inventory,AssemblerRules.CORE,187,8){
            @Override public boolean mayPlace(ItemStack stack){return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals("thaumicenergistics:knowledge_core");}
            @Override public int getMaxStackSize(){return 1;}
        });
        firstPattern=slots.size();for(int i=0;i<AssemblerRules.PATTERN_COUNT;i++)addDisplay(AssemblerRules.FIRST_PATTERN+i,26+i%7*18,25+i/7*18);
        firstUpgrade=slots.size();var upgrades=host==null?new SimpleContainer(4):host.upgrades().toContainer();
        for(int i=0;i<4;i++)addSlot(new Slot(upgrades,i,187,26+i*18){
            @Override public boolean mayPlace(ItemStack stack){return host==null?appeng.core.definitions.AEItems.SPEED_CARD.is(stack):host.upgrades().isItemValid(getContainerSlot(),stack);}
            @Override public int getMaxStackSize(){return 1;}
        });
        target=slots.size();addDisplay(AssemblerRules.TARGET,14,87);
        firstArmor=slots.size();var types=new EquipmentSlot[]{EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
        for(int i=0;i<4;i++){var type=types[i];addSlot(new Slot(inventory,AssemblerRules.FIRST_ARMOR+i,210,26+i*18){
            @Override public boolean mayPlace(ItemStack stack){return stack.getItem() instanceof IVisDiscountGear&&stack.canEquip(type,owner);}
            @Override public int getMaxStackSize(){return 1;}
        });}
    }
    private void addDisplay(int index,int x,int y){addSlot(new Slot(inventory,index,x,y){
        @Override public boolean mayPlace(ItemStack stack){return false;}
        @Override public boolean mayPickup(Player player){return false;}
    });}
    public static int findNetworkTool(Inventory inventory){for(int i=0;i<inventory.getContainerSize();i++)if(inventory.getItem(i).getItem() instanceof NetworkToolItem)return i;return -1;}
    public boolean hasNetworkTool(){return toolSlot>=0;}
    public AssemblerStatePayload state(){return state;}
    public float progress(){return AssemblerRules.progress(displayTicks,state.speed(),state.crafting());}
    public void clientTick(){if(state.crafting()&&displayTicks<AssemblerRules.BASE_TICKS)displayTicks++;}
    public void receive(AssemblerStatePayload update){if(host==null&&owner.level().isClientSide&&update.menuId()==containerId){state=update;displayTicks=state.ticks();}}
    @Override public void broadcastChanges(){
        super.broadcastChanges();if(host==null||!(owner instanceof ServerPlayer player)||!stillValid(owner))return;
        if(lastSent!=null&&player.tickCount%AssemblerRules.SYNC_INTERVAL!=0)return;
        var update=host.snapshot(containerId);if(!Objects.equals(update,lastSent)){PacketDistributor.sendToPlayer(player,update);lastSent=update;}
    }
    private boolean displaySlot(int index){return index>=firstPattern&&index<firstUpgrade||index==target;}
    private boolean boundTool(Slot slot){return hasNetworkTool()&&slot.container==owner.getInventory()&&slot.getContainerSlot()==toolSlot;}
    @Override public boolean stillValid(Player player){return player==owner&&!(player instanceof net.neoforged.neoforge.common.util.FakePlayer)
            &&(!hasNetworkTool()||player.level().isClientSide||player.getInventory().getItem(toolSlot)==tool)&&(host==null?player.level().isClientSide:host.valid(player));}
    @Override public void clicked(int index,int button,ClickType type,Player player){
        if(!stillValid(player)||index>=0&&index<slots.size()&&(displaySlot(index)||boundTool(getSlot(index))))return;
        if(type==ClickType.SWAP&&hasNetworkTool()&&button==toolSlot)return;
        super.clicked(index,button,type,player);
    }
    @Override public boolean canTakeItemForPickAll(ItemStack stack,Slot slot){return !displaySlot(slot.index)&&!boundTool(slot)&&super.canTakeItemForPickAll(stack,slot);}
    @Override public ItemStack quickMoveStack(Player player,int index){
        if(!stillValid(player)||index<0||index>=slots.size()||displaySlot(index)||boundTool(getSlot(index)))return ItemStack.EMPTY;
        var slot=getSlot(index);var stack=slot.getItem();if(stack.isEmpty())return ItemStack.EMPTY;boolean moved=false;
        if(index<36){
            if(Upgrades.isUpgradeCardItem(stack)){moved=moveItemStackTo(stack,firstUpgrade,firstUpgrade+4,false);if(!moved&&hasNetworkTool())moved=moveItemStackTo(stack,firstTool,firstTool+9,false);}
            else if(getSlot(firstCore).mayPlace(stack))moved=moveItemStackTo(stack,firstCore,firstCore+1,false);
            else if(stack.getItem() instanceof IVisDiscountGear)moved=moveItemStackTo(stack,firstArmor,firstArmor+4,false);
            if(!moved)moved=moveItemStackTo(stack,index<9?9:0,index<9?36:9,false);
        }else moved=moveItemStackTo(stack,0,9,false)||moveItemStackTo(stack,9,36,false);
        if(moved){if(stack.isEmpty())slot.set(ItemStack.EMPTY);else slot.setChanged();broadcastChanges();}
        return ItemStack.EMPTY;
    }
}
