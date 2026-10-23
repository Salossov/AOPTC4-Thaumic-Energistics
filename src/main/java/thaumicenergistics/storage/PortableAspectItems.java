package thaumicenergistics.storage;

import java.util.*;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.*;
import thaumicenergistics.items.VibrationChamberItem;

/** Own addon containers plus the core's public pure conversions. No core data access. */
public final class PortableAspectItems {
    public static Optional<EssentiaItemApi.Contents> inspect(ItemStack stack){
        if(!(stack.getItem() instanceof VibrationChamberItem))return EssentiaItemApi.inspect(stack);
        var stored=VibrationChamberItem.contents(stack);if(stored.amount()>0&&stored.aspect()==null)return Optional.empty();
        return Optional.of(new EssentiaItemApi.Contents(64,true,stored.aspect(),stored.amount(),null));
    }
    public static Optional<EssentiaItemApi.TransferResult> fill(ItemStack stack,Aspect aspect,int offered){
        if(!(stack.getItem() instanceof VibrationChamberItem))return EssentiaItemApi.fill(stack,aspect,offered);
        Objects.requireNonNull(aspect);var old=VibrationChamberItem.contents(stack);
        if(!VibrationChamberItem.accepts(aspect)||offered<=0||old.amount()>0&&old.aspect()!=aspect)return Optional.empty();
        int accepted=Math.min(64-old.amount(),offered);if(accepted<=0)return Optional.empty();
        var result=stack.copyWithCount(1);VibrationChamberItem.contents(result,new ChamberContents(aspect.getTag(),old.amount()+accepted));
        return Optional.of(new EssentiaItemApi.TransferResult(accepted,result));
    }
    public static Optional<EssentiaItemApi.TransferResult> drain(ItemStack stack,Aspect aspect,int requested){
        if(!(stack.getItem() instanceof VibrationChamberItem))return EssentiaItemApi.drain(stack,aspect,requested);
        Objects.requireNonNull(aspect);var old=VibrationChamberItem.contents(stack);int amount=Math.min(requested,old.amount());
        if(old.aspect()!=aspect||amount<=0)return Optional.empty();
        var result=stack.copyWithCount(1);VibrationChamberItem.contents(result,new ChamberContents(old.tag(),old.amount()-amount));
        return Optional.of(new EssentiaItemApi.TransferResult(amount,result));
    }
    private PortableAspectItems(){}
}
