package thaumicenergistics.menu;

import appeng.api.config.ViewItems;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.blockentity.storage.MEChestBlockEntity;
import appeng.util.Platform;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.storage.*;

/** ContainerEssentiaCell: private temporary work slots, powered local cell only, no crafting. */
public final class CellTerminalHost implements TerminalHost{
    private final MEChestBlockEntity chest;
    private final ItemStack boundCell;
    private final TerminalInventory inventory=new TerminalInventory(()->{});
    private Aspect selected;
    private int sort;
    private ViewItems view;
    public CellTerminalHost(MEChestBlockEntity chest){
        this.chest=chest;boundCell=chest.getCell();var settings=settings();sort=settings.sort();view=ViewItems.values()[settings.view()];
    }
    private TerminalSettings settings(){return boundCell.getOrDefault(ThaumicEnergistics.TERMINAL_SETTINGS,TerminalSettings.DEFAULT);}
    @Override public TerminalInventory inventory(){return inventory;}
    @Override public boolean valid(Player player){
        var pos=chest.getBlockPos();var level=chest.getLevel();
        return !(player instanceof net.neoforged.neoforge.common.util.FakePlayer)&&level==player.level()&&level.hasChunkAt(pos)&&!chest.isRemoved()
                &&level.getBlockEntity(pos)==chest&&chest.getCell()==boundCell&&boundCell.getItem() instanceof EssentiaCellItem
                &&player.distanceToSqr(pos.getCenter())<=64&&Platform.hasPermissions(new appeng.api.util.DimensionalBlockPos(chest),player);
    }
    @Override public MEStorage rawStorage(){return chest.isPowered()?chest.getCellInventory(0):null;}
    @Override public IEnergySource transferEnergy(){return chest;}
    @Override public IGridNode getActionableNode(){return chest.getActionableNode();}
    @Override public List<TerminalEntry> entries(){
        var raw=rawStorage();if(raw==null)return List.of();var counts=new KeyCounter();raw.getAvailableStacks(counts);var list=new ArrayList<TerminalEntry>();
        for(var entry:counts)if(entry.getKey() instanceof EssentiaKey key&&entry.getLongValue()>0)list.add(new TerminalEntry(key,entry.getLongValue(),false));
        return List.copyOf(list);
    }
    @Override public int sort(){return sort;}
    @Override public ViewItems view(){return view;}
    @Override public Aspect selected(){return selected;}
    @Override public void selected(Aspect value){selected=value;}
    @Override public void rotateSort(boolean backwards){
        var previous=settings();sort=1-previous.sort();
        // Keep HandlerItemEssentiaCell's omitted default write: the old nondefault
        // persists when the current GUI is switched back to the default mode.
        if(sort!=0){boundCell.set(ThaumicEnergistics.TERMINAL_SETTINGS,new TerminalSettings(sort,previous.view()));chest.setChanged();}
    }
    @Override public void rotateView(boolean backwards){
        var previous=settings();var values=ViewItems.values();view=values[(previous.view()+(backwards?values.length-1:1))%values.length];
        if(view!=ViewItems.ALL){boundCell.set(ThaumicEnergistics.TERMINAL_SETTINGS,new TerminalSettings(previous.sort(),view.ordinal()));chest.setChanged();}
    }
    @Override public void removed(Player player){
        if(!player.level().isClientSide)for(int slot=0;slot<2;slot++){var stack=inventory.removeItemNoUpdate(slot);if(!stack.isEmpty())player.drop(stack,false);}
    }
    @Override public ItemStack getMainMenuIcon(){return boundCell.copy();}
    @Override public void returnToMainMenu(Player player,appeng.menu.ISubMenu submenu){}
}
