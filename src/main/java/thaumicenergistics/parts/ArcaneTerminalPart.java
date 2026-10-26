package thaumicenergistics.parts;

import appeng.api.config.*;
import appeng.api.implementations.items.*;
import appeng.api.networking.*;
import appeng.api.networking.ticking.*;
import appeng.api.parts.*;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.*;
import appeng.api.util.AECableType;
import appeng.api.ids.AEComponents;
import appeng.parts.AEBasePart;
import appeng.util.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.component.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspects;
import thaumcraft.api.wands.WandApi;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.crafting.*;
import thaumicenergistics.menu.ArcaneTerminalMenu;
import thaumicenergistics.vis.DigiVisSourceData;

/** Original twenty-slot arcane terminal; its inventory belongs to the part. */
public final class ArcaneTerminalPart extends AEBasePart implements IGridTickable,ISubMenuHost {
    private static final IPartModel[] OFF=new IPartModel[4],ON=new IPartModel[4];
    static{for(int i=0;i<4;i++){OFF[i]=model(i,false);ON[i]=model(i,true);}}
    private static IPartModel model(int rotation,boolean on){
        var ids=List.of(ThaumicEnergistics.id("part/arcane_crafting_terminal_"+rotation+"_"+(on?"on":"off")));
        return new IPartModel(){@Override public List<ResourceLocation> getModels(){return ids;}};
    }
    private static final Component VIS_SOURCE=Component.translatable("thaumicenergistics.gui.digivis.source.name");
    private static final EquipmentSlot[] ARMOR={EquipmentSlot.HEAD,EquipmentSlot.CHEST,EquipmentSlot.LEGS,EquipmentSlot.FEET};
    private final ArcaneTerminalInventory inventory=new ArcaneTerminalInventory(this::inventoryChanged);
    private final Set<ArcaneTerminalMenu> listeners=new HashSet<>();
    private SortOrder sort=SortOrder.NAME;
    private SortDir direction=SortDir.ASCENDING;
    private ViewItems view=ViewItems.ALL;
    private DigiVisSourceData visSource;
    private int rotation;
    public ArcaneTerminalPart(IPartItem<?> item){super(item);getMainNode().setIdlePowerUsage(.5).setFlags(GridFlags.REQUIRE_CHANNEL).addService(IGridTickable.class,this);}
    @Override public Component getName(){return Component.translatable("thaumicenergistics.gui.arcane.crafting.terminal.title");}
    private void dirty(){if(getHost()!=null)getHost().markForSave();}
    private void inventoryChanged(int slot){dirty();if(listeners!=null)for(var menu:List.copyOf(listeners))menu.inventoryChanged();}
    public ArcaneTerminalInventory inventory(){return inventory;}
    public void listen(ArcaneTerminalMenu menu){listeners.add(menu);}
    public void unlisten(ArcaneTerminalMenu menu){listeners.remove(menu);}
    public SortOrder sort(){return sort;}
    public SortDir direction(){return direction;}
    public ViewItems view(){return view;}
    public void sorts(SortOrder next,SortDir dir,ViewItems mode){sort=next;direction=dir;view=mode;dirty();}
    public boolean valid(Player player){
        if(getHost()==null||player instanceof net.neoforged.neoforge.common.util.FakePlayer)return false;
        var tile=getBlockEntity();var pos=tile.getBlockPos();
        return player.level()==getLevel()&&getLevel().hasChunkAt(pos)&&!tile.isRemoved()&&getLevel().getBlockEntity(pos)==tile
                &&getHost().getPart(getSide())==this&&player.distanceToSqr(pos.getCenter())<=64&&Platform.hasPermissions(getHost().getLocation(),player);
    }
    public MEStorage network(){var grid=getMainNode().getGrid();return grid==null?null:grid.getStorageService().getInventory();}
    public List<ItemTerminalEntry> entries(){
        var grid=getMainNode().getGrid();if(grid==null||!isActive())return List.of();var entries=new LinkedHashMap<AEItemKey,ItemTerminalEntry>();
        for(var entry:grid.getStorageService().getCachedInventory())if(entry.getKey() instanceof AEItemKey key&&entry.getLongValue()>0)
            entries.put(key,new ItemTerminalEntry(key,entry.getLongValue(),false));
        for(var candidate:grid.getCraftingService().getCraftables(AEItemKey::is))if(candidate instanceof AEItemKey key)
            entries.put(key,new ItemTerminalEntry(key,entries.containsKey(key)?entries.get(key).amount():0,true));
        return List.copyOf(entries.values());
    }
    public void swapArmor(Player player){
        for(int i=0;i<4;i++){var stored=inventory.getItem(16+i);var equipped=player.getItemBySlot(ARMOR[i]);player.setItemSlot(ARMOR[i],stored);inventory.setItem(16+i,equipped);}
        player.inventoryMenu.broadcastChanges();dirty();
    }
    @Override public TickingRequest getTickingRequest(IGridNode node){return new TickingRequest(2,20,false);}
    @Override public TickRateModulation tickingRequest(IGridNode node,int elapsed){
        if(visSource==null)return TickRateModulation.IDLE;var wand=inventory.getItem(9);if(!ArcaneTerminalInventory.validWand(wand))return TickRateModulation.IDLE;
        boolean room=false;for(var aspect:Aspects.PRIMALS)if(WandApi.stored(wand,aspect)<WandApi.capacity(wand)){room=true;break;}
        if(!room)return TickRateModulation.IDLE;var source=visSource.resolve(getLevel().getServer(),getMainNode().getGrid());if(source==null)return TickRateModulation.IDLE;
        for(var aspect:Aspects.PRIMALS){int wanted=WandApi.capacity(wand)-WandApi.stored(wand,aspect);if(wanted<=0)continue;
            int drained=source.consumeVis(aspect,wanted);if(drained>0){WandApi.set(wand,aspect,WandApi.stored(wand,aspect)+drained);inventory.touch(9);}}
        return TickRateModulation.URGENT;
    }
    @Override public boolean useStandardMemoryCard(){return false;}
    @Override public boolean onUseItemOn(ItemStack held,Player player,InteractionHand hand,Vec3 hit){
        if(held.getItem() instanceof IMemoryCard card){
            if(isClientSide())return true;if(!valid(player))return true;
            var name=held.get(AEComponents.EXPORTED_SETTINGS_SOURCE);
            if(VIS_SOURCE.equals(name)){
                visSource=DigiVisSourceData.read(held.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag());
                if(visSource!=null)card.notifyUser(player,MemoryCardMessages.SETTINGS_LOADED);dirty();
            }else if(name==null){visSource=null;card.notifyUser(player,MemoryCardMessages.SETTINGS_CLEARED);dirty();}
            return true;
        }
        if(player.isShiftKeyDown())return false;
        if(InteractionUtil.canWrenchRotate(held)){
            if(!isClientSide()&&valid(player)){rotation=switch(rotation){case 0->1;case 1->3;case 3->2;default->0;};dirty();getHost().markForUpdate();}return true;
        }
        return onUseWithoutItem(player,hit);
    }
    @Override public boolean onUseWithoutItem(Player player,Vec3 hit){
        if(player.isShiftKeyDown())return false;
        if(player instanceof ServerPlayer server&&valid(player))server.openMenu(new SimpleMenuProvider((id,inv,owner)->new ArcaneTerminalMenu(id,inv,this),getName()));
        return true;
    }
    @Override public void returnToMainMenu(Player player,appeng.menu.ISubMenu submenu){onUseWithoutItem(player,Vec3.atCenterOf(getBlockEntity().getBlockPos()));}
    @Override public ItemStack getMainMenuIcon(){return new ItemStack(ThaumicEnergistics.ARCANE_TERMINAL.get());}
    @Override public int getLightLevel(){return isActive()?9:0;}
    @Override public float getCableConnectionLength(AECableType cable){return 2;}
    @Override public void getBoxes(IPartCollisionHelper boxes){boxes.addBox(2,2,14,14,14,16);boxes.addBox(5,5,13,11,11,14);}
    @Override public IPartModel getStaticModels(){return (isActive()?ON:OFF)[rotation];}
    private void settings(CompoundTag data,HolderLookup.Provider registries){
        inventory.save(data,registries);if(sort!=SortOrder.NAME)data.putString("SortOrder",sort.name());if(direction!=SortDir.ASCENDING)data.putString("SortDirection",direction.name());if(view!=ViewItems.ALL)data.putString("ViewMode",view.name());
    }
    private void loadSettings(CompoundTag data,HolderLookup.Provider registries){
        inventory.load(data,registries);sort=parse(SortOrder.values(),data.getString("SortOrder"),SortOrder.NAME);direction=parse(SortDir.values(),data.getString("SortDirection"),SortDir.ASCENDING);view=parse(ViewItems.values(),data.getString("ViewMode"),ViewItems.ALL);
    }
    private static <T extends Enum<T>> T parse(T[] values,String name,T fallback){for(var value:values)if(value.name().equals(name))return value;return fallback;}
    @Override public void writeToNBT(CompoundTag data,HolderLookup.Provider registries){super.writeToNBT(data,registries);settings(data,registries);if(visSource!=null)data.put("VisInterface",visSource.write());if(rotation!=0)data.putByte("partRotation",(byte)rotation);}
    @Override public void readFromNBT(CompoundTag data,HolderLookup.Provider registries){super.readFromNBT(data,registries);loadSettings(data,registries);visSource=data.contains("VisInterface")?DigiVisSourceData.read(data.getCompound("VisInterface")):null;rotation=Math.floorMod(data.getByte("partRotation"),4);}
    @Override public void exportSettings(SettingsFrom from,DataComponentMap.Builder builder){
        super.exportSettings(from,builder);if(from!=SettingsFrom.DISMANTLE_ITEM)return;var data=new CompoundTag();settings(data,getLevel().registryAccess());builder.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
    }
    @Override public void importSettings(SettingsFrom from,DataComponentMap components,Player player){super.importSettings(from,components,player);if(from!=SettingsFrom.DISMANTLE_ITEM)return;loadSettings(components.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag(),getLevel().registryAccess());visSource=null;dirty();}
    @Override public void addAdditionalDrops(List<ItemStack> drops,boolean wrenched){if(!wrenched)for(int i=0;i<20;i++)if(i!=10&&!inventory.getItem(i).isEmpty())drops.add(inventory.getItem(i).copy());}
    @Override public void clearContent(){inventory.clearContent();}
    @Override public void writeToStream(RegistryFriendlyByteBuf buffer){super.writeToStream(buffer);buffer.writeByte(rotation);}
    @Override public boolean readFromStream(RegistryFriendlyByteBuf buffer){boolean changed=super.readFromStream(buffer);int previous=rotation;rotation=Math.floorMod(buffer.readByte(),4);return changed||previous!=rotation;}
}
