package thaumicenergistics.storage;

import appeng.api.config.Actionable;
import appeng.api.networking.ticking.TickRateModulation;
import java.util.function.BiFunction;
import net.minecraft.nbt.CompoundTag;
import thaumcraft.api.aspects.*;

/** TileEssentiaVibrationChamber's elapsed-tick arithmetic, including integer truncation. */
public final class VibrationBurnState {
    private int remaining,speed,total;
    private double dilation=1,powerPerProcessingTick,powerPerTick;
    private Aspect aspect;
    public int remaining(){return remaining;}
    public int speed(){return speed;}
    public Aspect aspect(){return aspect;}
    public double dilation(){return dilation;}
    public double powerPerTick(){return powerPerTick;}
    public double maximumPowerPerTick(){return powerPerProcessingTick*2;}
    public int displayRemaining(){return (int)(remaining/dilation);}
    public int displayTotal(){return (int)(total/dilation);}
    public void clampSpeed(){speed=Math.max(20,Math.min(200,speed));}
    public void idle(){powerPerTick=0;speed=20;}
    public boolean start(Aspect fuel,int coalBurnTime){
        int ticks;
        if(fuel==Aspects.FIRE){ticks=coalBurnTime/2;powerPerProcessingTick=5;}
        else if(fuel==Aspects.ENERGY){ticks=(int)(coalBurnTime/1.6F);powerPerProcessingTick=8;}
        else{total=0;return false;}
        total=ticks;if(ticks<=0)return false;aspect=fuel;remaining+=ticks;return true;
    }
    public TickRateModulation process(int elapsed,BiFunction<Double,Actionable,Double> inject){
        if(remaining==0)return TickRateModulation.IDLE;
        clampSpeed();dilation=speed/100.0;double ticks=elapsed*dilation;
        remaining=(int)(remaining-ticks);powerPerTick=ticks*powerPerProcessingTick/elapsed;
        if(remaining<=0){ticks+=remaining;remaining=0;aspect=null;}
        double power=ticks*powerPerProcessingTick;var rate=TickRateModulation.SLOWER;
        if(inject!=null){
            double rejected=inject.apply(power,Actionable.SIMULATE);
            if(rejected>0){power=Math.max(0,power-rejected);speed-=elapsed;}
            else{rate=TickRateModulation.FASTER;speed+=elapsed;}
            if(power>0)inject.apply(power,Actionable.MODULATE);
        }
        return rate;
    }
    public void resetForDismantle(){speed=20;remaining=0;}
    public void save(CompoundTag data){
        if(remaining>0){data.putInt("TRemain",remaining);data.putInt("ProcSpeed",speed);data.putDouble("PwrPerTick",powerPerProcessingTick);if(aspect!=null)data.putString("ProcAspect",aspect.getTag());}
    }
    public void load(CompoundTag data){
        remaining=data.getInt("TRemain");speed=0;powerPerProcessingTick=0;aspect=null;dilation=1;total=0;powerPerTick=0;
        if(remaining>0){speed=data.getInt("ProcSpeed");powerPerProcessingTick=data.getDouble("PwrPerTick");aspect=Aspect.getAspect(data.getString("ProcAspect"));}
        // Source does not persist dilation, display total or moving power average.
    }
}
