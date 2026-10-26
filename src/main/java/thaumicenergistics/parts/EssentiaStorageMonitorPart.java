package thaumicenergistics.parts;

import appeng.api.implementations.parts.IStorageMonitorPart;
import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IStackWatcher;
import appeng.api.networking.storage.IStorageWatcherNode;
import appeng.api.parts.*;
import appeng.api.stacks.AEKey;
import appeng.api.util.AECableType;
import appeng.parts.AEBasePart;
import appeng.util.InteractionUtil;
import appeng.util.Platform;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.IAspectItem;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.EssentiaBusMenu;
import thaumicenergistics.storage.EssentiaKey;

/** PartEssentiaStorageMonitor: a display, not an item/fluid inventory. */
public class EssentiaStorageMonitorPart extends AEBasePart implements IStorageMonitorPart,IStorageWatcherNode {
    private static final IPartModel OFF=model("off"),ON=model("on"),EMPTY_OFF=model("empty_off"),EMPTY_ON=model("empty_on");
    private static IPartModel model(String state){
        var ids=List.of(ThaumicEnergistics.id("part/essentia_storage_monitor_"+state));
        return new IPartModel(){@Override public List<ResourceLocation> getModels(){return ids;}};
    }
    private EssentiaKey tracked;
    private long amount;
    private boolean locked;
    private IStackWatcher watcher;
    public EssentiaStorageMonitorPart(IPartItem<?> item){
        super(item);getMainNode().setIdlePowerUsage(.0625).setFlags(GridFlags.REQUIRE_CHANNEL).addService(IStorageWatcherNode.class,this);
    }
    @Override public EssentiaKey getDisplayed(){return tracked;}
    @Override public long getAmount(){return amount;}
    @Override public boolean isLocked(){return locked;}
    @Override public boolean isPowered(){return super.isPowered()&&!isMissingChannel();}
    @Override public boolean showNetworkInfo(UseOnContext context){return false;}
    @Override public boolean useStandardMemoryCard(){return false;}
    protected final boolean allowed(Player player){
        return !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)&&isActive()
                &&Platform.hasPermissions(getHost().getLocation(),player);
    }
    private void changed(){getHost().markForSave();getHost().markForUpdate();}
    private void configureWatcher(){
        if(watcher!=null){watcher.reset();if(tracked!=null)watcher.add(tracked);}
        var grid=getMainNode().getGrid();
        long next=tracked==null||grid==null?0:grid.getStorageService().getCachedInventory().get(tracked);
        if(next!=amount){amount=next;if(getHost()!=null)getHost().markForUpdate();}
    }
    @Override public void updateWatcher(IStackWatcher value){watcher=value;configureWatcher();}
    @Override public void onStackChange(AEKey key,long stored){
        if(tracked!=null&&tracked.equals(key)&&amount!=stored){amount=stored;getHost().markForUpdate();}
    }
    @Override protected void onMainNodeStateChanged(IGridNodeListener.State reason){
        super.onMainNodeStateChanged(reason);if(getHost()!=null&&!isClientSide()){configureWatcher();getHost().markForUpdate();}
    }
    @Override public boolean onUseWithoutItem(Player player,Vec3 hit){
        if(isClientSide())return true;
        if(!allowed(player)||locked)return false;
        tracked=null;amount=0;configureWatcher();changed();return true;
    }
    @Override public boolean onUseItemOn(ItemStack held,Player player,InteractionHand hand,Vec3 hit){
        if(isClientSide())return true;
        if(!allowed(player))return false;
        if(!player.isShiftKeyDown()&&InteractionUtil.canWrenchRotate(held)){
            locked=!locked;player.displayClientMessage(Component.translatable(locked?"chat.ae2.isNowLocked":"chat.ae2.isNowUnlocked"),false);changed();return true;
        }
        // Empty and filled phials shared one whitelisted ItemEssence in 1.7.10.
        // The modern empty phial is a separate item, identified by the public container API.
        if(locked||(!(held.getItem() instanceof IAspectItem)
                &&thaumicenergistics.storage.PortableAspectItems.inspect(held).isEmpty()))return false;
        tracked=EssentiaBusMenu.aspectFromItem(held);amount=0;configureWatcher();changed();return true;
    }
    @Override public int getLightLevel(){return isActive()?9:0;}
    @Override public float getCableConnectionLength(AECableType type){return 3;}
    @Override public void getBoxes(IPartCollisionHelper boxes){boxes.addBox(2,2,14,14,14,16);boxes.addBox(5,5,13,11,11,14);}
    @Override public IPartModel getStaticModels(){return tracked==null?(isActive()?EMPTY_ON:EMPTY_OFF):(isActive()?ON:OFF);}
    @Override public void writeToNBT(CompoundTag tag,HolderLookup.Provider registries){
        super.writeToNBT(tag,registries);if(locked)tag.putBoolean("Locked",true);if(tracked!=null)tag.putString("TrackedAspect",tracked.tag());
    }
    @Override public void readFromNBT(CompoundTag tag,HolderLookup.Provider registries){
        super.readFromNBT(tag,registries);locked=tag.getBoolean("Locked");
        tracked=tag.getString("TrackedAspect").isEmpty()?null:EssentiaKey.ofTag(tag.getString("TrackedAspect"));amount=0;
    }
    @Override public void writeToStream(RegistryFriendlyByteBuf data){
        super.writeToStream(data);data.writeBoolean(locked);data.writeBoolean(tracked!=null);
        if(tracked!=null){data.writeUtf(tracked.tag());data.writeVarLong(amount);}
    }
    @Override public boolean readFromStream(RegistryFriendlyByteBuf data){
        boolean changed=super.readFromStream(data),oldLocked=locked;var old=tracked;long oldAmount=amount;
        locked=data.readBoolean();tracked=data.readBoolean()?EssentiaKey.ofTag(data.readUtf()):null;amount=tracked==null?0:data.readVarLong();
        return changed||locked!=oldLocked||!Objects.equals(old,tracked)||amount!=oldAmount;
    }
    @Override public boolean requireDynamicRender(){return true;}
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    @Override public void renderDynamic(float partial,com.mojang.blaze3d.vertex.PoseStack poses,net.minecraft.client.renderer.MultiBufferSource buffers,int light,int overlay){
        thaumicenergistics.client.EssentiaMonitorRendering.render(this,poses,buffers);
    }
}
