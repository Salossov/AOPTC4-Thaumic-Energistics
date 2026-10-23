package thaumicenergistics.crafting;

import java.util.function.IntConsumer;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.wands.WandApi;

/** PartArcaneCraftingTerminal's twenty shared slots, including its transient result. */
public final class ArcaneTerminalInventory extends SimpleContainer {
    public static final int WAND=9,RESULT=10,FIRST_VIEW=11,LAST_VIEW=15,FIRST_ARMOR=16;
    private final IntConsumer changed;
    private boolean quiet;
    public ArcaneTerminalInventory(IntConsumer changed){super(20);this.changed=changed;}
    public static boolean validWand(ItemStack stack){
        if(stack.isEmpty())return false;var kind=WandApi.kind(stack);return kind==WandApi.Kind.WAND||kind==WandApi.Kind.SCEPTRE;
    }
    public static boolean viewCell(ItemStack stack){return stack.getItem() instanceof appeng.items.storage.ViewCellItem;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){
        if(slot<0||slot>=20||slot==RESULT)return false;
        if(stack.isEmpty())return true;
        return slot==WAND?validWand(stack):slot>=FIRST_VIEW&&slot<=LAST_VIEW?viewCell(stack):true;
    }
    @Override public void setItem(int slot,ItemStack stack){super.setItem(slot,stack);notifySlot(slot);}
    @Override public ItemStack removeItem(int slot,int amount){var result=super.removeItem(slot,slot==RESULT?getItem(slot).getCount():amount);if(!result.isEmpty())notifySlot(slot);return result;}
    @Override public ItemStack removeItemNoUpdate(int slot){return getItem(slot);}
    @Override public void setChanged(){if(!quiet){super.setChanged();if(changed!=null)changed.accept(-1);}}
    private void notifySlot(int slot){if(!quiet&&changed!=null)changed.accept(slot);}
    public void result(ItemStack value){quiet=true;try{super.setItem(RESULT,value);}finally{quiet=false;}}
    public void touch(int slot){setChanged();notifySlot(slot);}
    public void save(CompoundTag data,HolderLookup.Provider registries){
        var slots=new ListTag();for(int slot=0;slot<20;slot++)if(!getItem(slot).isEmpty()){
            var entry=new CompoundTag();entry.putByte("Slot#",(byte)slot);entry.put("stack",getItem(slot).save(registries));slots.add(entry);
        }
        if(!slots.isEmpty())data.put("TEACT_Inventory",slots);
    }
    public void load(CompoundTag data,HolderLookup.Provider registries){
        quiet=true;
        try{
            super.clearContent();var entries=data.getList("TEACT_Inventory",Tag.TAG_COMPOUND);
            for(var raw:entries){var entry=(CompoundTag)raw;int slot=entry.getByte("Slot#");if(slot<0||slot>=20)continue;
                var value=ItemStack.parseOptional(registries,entry.getCompound("stack"));if(slot==WAND&&!value.isEmpty()&&!validWand(value))value=ItemStack.EMPTY;
                super.setItem(slot,value);
            }
        }finally{quiet=false;}
    }
}
