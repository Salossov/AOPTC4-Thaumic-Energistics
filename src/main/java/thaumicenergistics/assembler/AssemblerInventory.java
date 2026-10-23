package thaumicenergistics.assembler;

import java.util.function.IntConsumer;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/** Twenty-seven source slots: only core/armor are owned material; patterns/target are displays. */
public final class AssemblerInventory extends SimpleContainer {
    private final Predicate<ItemStack> core;
    private final IntConsumer changed;
    private boolean quiet;
    public AssemblerInventory(Predicate<ItemStack> core,IntConsumer changed){super(AssemblerRules.SLOTS);this.core=core;this.changed=changed;}
    @Override public boolean canPlaceItem(int slot,ItemStack stack){return slot==AssemblerRules.CORE&&(stack.isEmpty()||core.test(stack));}
    // Container has no per-index stack-limit override in 1.21.1; the real menu slots enforce one core/armor.
    @Override public void setChanged(){super.setChanged();if(!quiet&&changed!=null)changed.accept(-1);}
    @Override public void setItem(int slot,ItemStack stack){super.setItem(slot,stack);notifySlot(slot);}
    @Override public ItemStack removeItem(int slot,int amount){var removed=super.removeItem(slot,amount);if(!removed.isEmpty())notifySlot(slot);return removed;}
    private void notifySlot(int slot){if(!quiet&&changed!=null)changed.accept(slot);}
    public void display(int slot,ItemStack stack){
        if(slot<AssemblerRules.FIRST_PATTERN||slot>AssemblerRules.TARGET)throw new IllegalArgumentException("Not a display slot");
        quiet=true;try{super.setItem(slot,stack.copy());}finally{quiet=false;}
    }
    public CompoundTag save(HolderLookup.Provider registries){
        var tag=new CompoundTag();var slots=new ListTag();
        for(int i=0;i<AssemblerRules.SLOTS;i++)if(i==AssemblerRules.CORE||i>=AssemblerRules.FIRST_ARMOR){
            var stack=getItem(i);if(stack.isEmpty())continue;var entry=new CompoundTag();entry.putInt("Slot",i);entry.put("Stack",stack.save(registries));slots.add(entry);
        }
        tag.put("Owned",slots);return tag;
    }
    public void load(CompoundTag tag,HolderLookup.Provider registries){
        quiet=true;
        try{
            super.clearContent();
            for(var raw:tag.getList("Owned",Tag.TAG_COMPOUND)){
                var entry=(CompoundTag)raw;int slot=entry.getInt("Slot");if(slot!=AssemblerRules.CORE&&(slot<AssemblerRules.FIRST_ARMOR||slot>=AssemblerRules.SLOTS))continue;
                var stack=ItemStack.parseOptional(registries,entry.getCompound("Stack"));
                super.setItem(slot,stack);
            }
        }finally{quiet=false;}
    }
}
