package thaumicenergistics.menu;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.blocks.VibrationChamberBlockEntity;
import thaumicenergistics.network.ChamberStatePayload;

/** Original display-only container; no player or machine item slots. */
public final class VibrationChamberMenu extends AbstractContainerMenu {
    private final Player owner;
    private final VibrationChamberBlockEntity chamber;
    private ChamberStatePayload state;
    private float movingRemaining;
    private int averageRemaining;
    public VibrationChamberMenu(int id,Inventory inventory,VibrationChamberBlockEntity chamber){
        super(ThaumicEnergistics.VIBRATION_MENU.get(),id);owner=inventory.player;this.chamber=chamber;
        state=new ChamberStatePayload(id,"",0,"",0,0,0,0);if(chamber!=null)chamber.addListener(this);
    }
    public void onChamberUpdate(){
        if(owner instanceof ServerPlayer player&&player.containerMenu==this&&stillValid(player)){
            var burn=chamber.burn();var contents=chamber.stored();
            PacketDistributor.sendToPlayer(player,new ChamberStatePayload(containerId,contents.tag(),contents.amount(),burn.aspect()==null?"":burn.aspect().getTag(),(float)burn.powerPerTick(),(float)burn.maximumPowerPerTick(),burn.displayRemaining(),burn.displayTotal()));
        }
    }
    public void receive(ChamberStatePayload snapshot){
        if(chamber!=null||!owner.level().isClientSide||snapshot.containerId()!=containerId)return;state=snapshot;
        if(movingRemaining==0)movingRemaining=state.remaining();
        else{float inverse=state.remaining()>movingRemaining?.3F:1F;movingRemaining-=movingRemaining/(4F*inverse);movingRemaining+=state.remaining()/(4F*inverse);}
        averageRemaining=(int)movingRemaining;
    }
    public Aspect storedAspect(){return Aspect.getAspect(state.stored());}
    public int storedAmount(){return state.amount();}
    public Aspect processingAspect(){return Aspect.getAspect(state.processing());}
    public int ticksRemaining(){return averageRemaining;}
    public float powerPerTick(){return state.power();}
    public float powerPercent(){return 1F-(state.maximumPower()>0?state.power()/state.maximumPower():0F);}
    public float storedPercent(){return 1F-state.amount()/64F;}
    public float ticksPercent(){return 1F-(state.total()>0?(float)state.remaining()/state.total():0F);}
    @Override public boolean stillValid(Player player){return owner==player&&(chamber==null?player.level().isClientSide:chamber.valid(player));}
    @Override public ItemStack quickMoveStack(Player player,int slot){return ItemStack.EMPTY;}
    @Override public void removed(Player player){super.removed(player);if(chamber!=null)chamber.removeListener(this);}
}
