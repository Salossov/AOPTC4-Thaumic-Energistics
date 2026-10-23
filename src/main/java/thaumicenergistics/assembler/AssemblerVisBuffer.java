package thaumicenergistics.assembler;

import java.util.*;
import net.minecraft.nbt.CompoundTag;
import thaumcraft.api.aspects.Aspect;

/** Keeps the release's raw 1500-unit buffer and /10 GUI display; no silent unit rescaling. */
public final class AssemblerVisBuffer {
    public static final List<String> PRIMALS=List.of("aer","aqua","ignis","ordo","perditio","terra");
    private final int[] amounts=new int[6];
    public int get(int index){return amounts[index];}
    public int get(Aspect aspect){int index=PRIMALS.indexOf(aspect.getTag());return index<0?0:amounts[index];}
    public int room(int index){return Math.max(0,AssemblerRules.MAX_VIS-amounts[index]);}
    public void fill(){Arrays.fill(amounts,AssemblerRules.MAX_VIS);}
    public void clear(){Arrays.fill(amounts,0);}
    public int add(int index,int offered){
        if(offered<0)throw new IllegalArgumentException("Negative Vis refill");
        int accepted=Math.min(room(index),offered);amounts[index]+=accepted;return accepted;
    }
    public Map<Aspect,Integer> snapshot(){
        var result=new LinkedHashMap<Aspect,Integer>();for(int i=0;i<6;i++){var aspect=Aspect.getAspect(PRIMALS.get(i));if(aspect!=null)result.put(aspect,amounts[i]);}
        return Collections.unmodifiableMap(result);
    }
    public boolean canPay(Map<Aspect,Integer> costs){
        for(var entry:costs.entrySet())if(get(entry.getKey())<entry.getValue())return false;return true;
    }
    public boolean pay(Map<Aspect,Integer> costs){
        if(!canPay(costs))return false;
        // All arithmetic is validated before any debit, including unusually large third-party gear discounts.
        int[] changed=amounts.clone();
        for(var entry:costs.entrySet()){
            int index=PRIMALS.indexOf(entry.getKey().getTag());if(index<0)throw new IllegalArgumentException("Non-primal recipe cost");
            changed[index]=Math.subtractExact(changed[index],entry.getValue());
        }
        System.arraycopy(changed,0,amounts,0,6);return true;
    }
    public CompoundTag save(){var tag=new CompoundTag();for(int i=0;i<6;i++)if(amounts[i]!=0)tag.putInt(PRIMALS.get(i),amounts[i]);return tag;}
    public void load(CompoundTag tag){for(int i=0;i<6;i++)amounts[i]=Math.max(0,tag.getInt(PRIMALS.get(i)));}
}
