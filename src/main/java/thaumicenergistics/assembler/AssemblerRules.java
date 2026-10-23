package thaumicenergistics.assembler;

/** Numeric rules from ThE 1.1.3.0 TileArcaneAssembler, before any agreed bug fixes. */
public final class AssemblerRules {
    public static final int CORE=0,FIRST_PATTERN=1,PATTERN_COUNT=21,TARGET=22,FIRST_ARMOR=23,SLOTS=27;
    public static final int MAX_SPEED_UPGRADES=4,BASE_TICKS=20,VIS_SCALE=10,MAX_VIS=150*VIS_SCALE;
    public static final int REFILL_INTERVAL=5,SYNC_INTERVAL=5;
    public static final double IDLE_AE=0,ACTIVE_AE=1.5,WARP_POWER_PERCENT=.15;
    public static int ticksPerCraft(int upgrades){return BASE_TICKS-4*upgrades(upgrades);}
    public static double powerPerTick(int upgrades,float warpMultiplier){return (ACTIVE_AE+ACTIVE_AE*upgrades(upgrades)/2D)*warpMultiplier;}
    public static float warpMultiplier(int warp){return (float)(1D+warp*WARP_POWER_PERCENT);}
    public static int requiredVis(int wholeVis,float modifier){
        if(wholeVis<0||!Float.isFinite(modifier))throw new IllegalArgumentException("Invalid arcane recipe cost");
        int scaled=Math.multiplyExact(wholeVis,VIS_SCALE);
        double rounded=Math.ceil(modifier*scaled);
        if(rounded>Integer.MAX_VALUE||rounded<Integer.MIN_VALUE)throw new IllegalArgumentException("Arcane recipe cost overflow");
        return (int)rounded;
    }
    public static float progress(int ticks,int upgrades,boolean crafting){return crafting?Math.min(ticks/(float)ticksPerCraft(upgrades),1):0;}
    private static int upgrades(int count){if(count<0||count>MAX_SPEED_UPGRADES)throw new IllegalArgumentException("Invalid speed card count");return count;}
    private AssemblerRules(){}
}
