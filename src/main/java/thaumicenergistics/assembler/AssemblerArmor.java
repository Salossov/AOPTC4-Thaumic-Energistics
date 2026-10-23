package thaumicenergistics.assembler;

import java.util.*;
import java.util.function.Function;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.IVisDiscountGear;
import thaumcraft.api.IWarpingGear;
import thaumcraft.api.aspects.Aspect;

/** The four local armor slots are queried with a null wearer, as in VisCraftingHelper. */
public record AssemblerArmor(Map<Aspect,Float> modifiers,float warpMultiplier) {
    public AssemblerArmor{modifiers=Collections.unmodifiableMap(new LinkedHashMap<>(modifiers));}
    public static AssemblerArmor read(Container inventory,Function<Aspect,Float> sceptreModifier){
        var result=new LinkedHashMap<Aspect,Float>();
        for(var name:AssemblerVisBuffer.PRIMALS){
            var aspect=Objects.requireNonNull(Aspect.getAspect(name));float discount=0;
            for(int slot=AssemblerRules.FIRST_ARMOR;slot<AssemblerRules.SLOTS;slot++){
                var stack=inventory.getItem(slot);
                if(!stack.isEmpty()&&stack.getItem() instanceof IVisDiscountGear gear){
                    try{discount+=gear.getVisDiscount(stack,null,aspect)/100F;}catch(Exception ignored){}
                }
            }
            result.put(aspect,sceptreModifier.apply(aspect)-discount);
        }
        int warp=0;
        for(int slot=AssemblerRules.FIRST_ARMOR;slot<AssemblerRules.SLOTS;slot++){
            ItemStack stack=inventory.getItem(slot);
            if(!stack.isEmpty()&&stack.getItem() instanceof IWarpingGear gear){try{warp+=gear.getWarp(stack,null);}catch(Exception ignored){}}
        }
        return new AssemblerArmor(result,AssemblerRules.warpMultiplier(warp));
    }
    public Map<Aspect,Integer> costs(Map<Aspect,Integer> wholeVis){
        var result=new LinkedHashMap<Aspect,Integer>();
        wholeVis.forEach((aspect,amount)->result.put(aspect,AssemblerRules.requiredVis(amount,Objects.requireNonNull(modifiers.get(aspect)))));
        return Collections.unmodifiableMap(result);
    }
}
