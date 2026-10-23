package thaumicenergistics.parts;

import appeng.api.config.ViewItems;
import appeng.api.networking.GridFlags;
import appeng.api.parts.*;
import appeng.api.storage.ISubMenuHost;
import appeng.api.util.AECableType;
import appeng.parts.AEBasePart;
import appeng.util.InteractionUtil;
import appeng.util.Platform;
import appeng.util.SettingsFrom;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.EssentiaTerminalMenu;
import thaumicenergistics.storage.*;

/** PartEssentiaTerminal: saved work slots and modes, transient selection, native AE2 crafting submenus. */
public final class EssentiaTerminalPart extends AEBasePart implements thaumicenergistics.menu.TerminalHost {
    private static final IPartModel[] OFF=new IPartModel[4],ON=new IPartModel[4];
    static{for(int i=0;i<4;i++){OFF[i]=model(i,false);ON[i]=model(i,true);}}
    private static IPartModel model(int rotation,boolean on){
        var ids=List.of(ThaumicEnergistics.id("part/essentia_terminal_"+rotation+"_"+(on?"on":"off")));
        return new IPartModel(){@Override public List<ResourceLocation> getModels(){return ids;}};
    }
    private int rotation,sort;
    private ViewItems view=ViewItems.ALL;
    private Aspect selected;
    private final TerminalInventory inventory=new TerminalInventory(this::dirty);
    public EssentiaTerminalPart(IPartItem<?> item){super(item);getMainNode().setIdlePowerUsage(.5).setFlags(GridFlags.REQUIRE_CHANNEL);}
    private void dirty(){if(getHost()!=null)getHost().markForSave();}
    public TerminalInventory inventory(){return inventory;}
    @Override public boolean valid(Player player){return EssentiaTerminalMenu.partValid(this,player);}
    @Override public appeng.api.storage.MEStorage rawStorage(){var grid=getMainNode().getGrid();return grid==null?null:grid.getStorageService().getInventory();}
    @Override public appeng.api.networking.energy.IEnergySource transferEnergy(){var grid=getMainNode().getGrid();return grid==null?null:grid.getEnergyService();}
    @Override public boolean supportsCrafting(){return true;}
    @Override public appeng.menu.locator.MenuHostLocator craftingLocator(){return appeng.menu.locator.MenuLocators.forPart(this);}
    public int sort(){return sort;}
    public ViewItems view(){return view;}
    public Aspect selected(){return selected;}
    public void selected(Aspect value){selected=value;}
    public void rotateSort(boolean backwards){sort=1-sort;dirty();}
    public void rotateView(boolean backwards){var values=ViewItems.values();view=values[(view.ordinal()+(backwards?values.length-1:1))%values.length];dirty();}
    public List<TerminalEntry> entries(){
        var grid=getMainNode().getGrid();if(grid==null)return List.of();
        var entries=new LinkedHashMap<EssentiaKey,TerminalEntry>();
        for(var entry:grid.getStorageService().getCachedInventory())if(entry.getKey() instanceof EssentiaKey key&&entry.getLongValue()>0)
            entries.put(key,new TerminalEntry(key,entry.getLongValue(),false));
        for(var craftable:grid.getCraftingService().getCraftables(key->key instanceof EssentiaKey))if(craftable instanceof EssentiaKey key)
            entries.put(key,new TerminalEntry(key,entries.containsKey(key)?entries.get(key).amount():0,true));
        return List.copyOf(entries.values());
    }
    @Override public boolean useStandardMemoryCard(){return false;}
    @Override public boolean onUseWithoutItem(Player player,Vec3 hit){
        if(player.isShiftKeyDown())return false;
        if(isClientSide())return true;
        if(player instanceof net.minecraft.server.level.ServerPlayer server&&EssentiaTerminalMenu.partValid(this,player))
            server.openMenu(new SimpleMenuProvider((id,inv,owner)->new EssentiaTerminalMenu(id,inv,this),getName()));
        return true;
    }
    @Override public boolean onUseItemOn(ItemStack held,Player player,InteractionHand hand,Vec3 hit){
        if(player.isShiftKeyDown())return false;
        if(InteractionUtil.canWrenchRotate(held)){
            if(isClientSide())return true;
            if(!EssentiaTerminalMenu.partValid(this,player))return false;
            rotation=switch(rotation){case 0->1;case 1->3;case 3->2;default->0;};dirty();getHost().markForUpdate();return true;
        }
        return onUseWithoutItem(player,hit);
    }
    @Override public void returnToMainMenu(Player player,appeng.menu.ISubMenu submenu){onUseWithoutItem(player,Vec3.atCenterOf(getBlockEntity().getBlockPos()));}
    @Override public ItemStack getMainMenuIcon(){return new ItemStack(ThaumicEnergistics.ESSENTIA_TERMINAL.get());}
    @Override public int getLightLevel(){return isActive()?9:0;}
    @Override public float getCableConnectionLength(AECableType type){return 1;}
    @Override public void getBoxes(IPartCollisionHelper boxes){boxes.addBox(2,2,14,14,14,16);boxes.addBox(4,4,13,12,12,14);boxes.addBox(5,5,12,11,11,13);}
    @Override public IPartModel getStaticModels(){return (isActive()?ON:OFF)[rotation];}
    private void settings(CompoundTag data,HolderLookup.Provider lookup){
        if(sort!=0)data.putInt("sortMode",sort);if(view!=ViewItems.ALL)data.putInt("ViewMode",view.ordinal());
        if(!inventory.isEmpty()){
            var slots=new CompoundTag();for(int i=0;i<2;i++)if(!inventory.getItem(i).isEmpty())slots.put(Integer.toString(i),inventory.getItem(i).save(lookup));data.put("slots",slots);
        }
    }
    private void loadSettings(CompoundTag data,HolderLookup.Provider lookup){
        sort=data.getInt("sortMode")==1?1:0;int ordinal=data.getInt("ViewMode");view=ordinal>=0&&ordinal<ViewItems.values().length?ViewItems.values()[ordinal]:ViewItems.ALL;
        var slots=data.getCompound("slots");for(int i=0;i<2;i++)inventory.setItem(i,ItemStack.parseOptional(lookup,slots.getCompound(Integer.toString(i))));
    }
    @Override public void writeToNBT(CompoundTag data,HolderLookup.Provider lookup){super.writeToNBT(data,lookup);settings(data,lookup);if(rotation!=0)data.putByte("partRotation",(byte)rotation);}
    @Override public void readFromNBT(CompoundTag data,HolderLookup.Provider lookup){super.readFromNBT(data,lookup);loadSettings(data,lookup);rotation=Math.floorMod(data.getByte("partRotation"),4);selected=null;}
    @Override public void exportSettings(SettingsFrom from,DataComponentMap.Builder builder){
        super.exportSettings(from,builder);if(from!=SettingsFrom.DISMANTLE_ITEM)return;
        var data=new CompoundTag();settings(data,getLevel().registryAccess());builder.set(DataComponents.CUSTOM_DATA,CustomData.of(data));
    }
    @Override public void importSettings(SettingsFrom from,DataComponentMap components,Player player){
        super.importSettings(from,components,player);if(from!=SettingsFrom.DISMANTLE_ITEM)return;
        loadSettings(components.getOrDefault(DataComponents.CUSTOM_DATA,CustomData.EMPTY).copyTag(),getLevel().registryAccess());dirty();
    }
    @Override public void addAdditionalDrops(List<ItemStack> drops,boolean wrenched){if(!wrenched)for(int i=0;i<2;i++)if(!inventory.getItem(i).isEmpty())drops.add(inventory.getItem(i).copy());}
    @Override public void clearContent(){inventory.clearContent();}
    @Override public void writeToStream(RegistryFriendlyByteBuf data){super.writeToStream(data);data.writeByte(rotation);}
    @Override public boolean readFromStream(RegistryFriendlyByteBuf data){boolean changed=super.readFromStream(data);int old=rotation;rotation=Math.floorMod(data.readByte(),4);return changed||old!=rotation;}
}
