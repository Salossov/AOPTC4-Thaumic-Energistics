package thaumicenergistics.assembler;

/** Pure source-value checks, deferred until the agreed consolidated verification stage. */
public final class AssemblerRulesTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static void rejects(Runnable operation,String message){boolean rejected=false;try{operation.run();}catch(IllegalArgumentException|ArithmeticException expected){rejected=true;}check(rejected,message);}
    public static void main(String[] args){
        check(AssemblerRules.SLOTS==27&&AssemblerRules.CORE==0&&AssemblerRules.FIRST_PATTERN==1&&AssemblerRules.PATTERN_COUNT==21&&AssemblerRules.TARGET==22&&AssemblerRules.FIRST_ARMOR==23,"Source inventory layout");
        check(AssemblerRules.MAX_VIS==1500&&AssemblerRules.VIS_SCALE==10,"Source buffer and displayed units retained");
        for(int speed=0;speed<=4;speed++){
            check(AssemblerRules.ticksPerCraft(speed)==20-speed*4,"Craft duration by speed card count");
            check(AssemblerRules.powerPerTick(speed,1)==1.5+.75*speed,"Energy per paid tick");
        }
        check(AssemblerRules.warpMultiplier(0)==1F,"No armor warp multiplier");
        check(AssemblerRules.warpMultiplier(4)==1.6F,"Four armor warp at15percent");
        check(AssemblerRules.requiredVis(3,.8F)==24,"Raw recipe cost uses source factor10");
        check(AssemblerRules.requiredVis(1,.71F)==8,"Source cost rounds upward");
        check(AssemblerRules.requiredVis(1,-.1F)==-1,"Unclamped third-party gear discount quirk retained");
        check(AssemblerRules.progress(0,0,true)==0,"Beginning progress");
        check(AssemblerRules.progress(2,4,true)==.5F,"Fast machine progress");
        check(AssemblerRules.progress(20,4,true)==1,"Client progress saturates");
        check(AssemblerRules.progress(20,4,false)==0,"Idle hides stale progress");
        rejects(()->AssemblerRules.ticksPerCraft(5),"Reject excessive speed cards");
        rejects(()->AssemblerRules.ticksPerCraft(-1),"Reject negative speed cards");
        rejects(()->AssemblerRules.requiredVis(Integer.MAX_VALUE,1),"Reject raw cost overflow");
        rejects(()->AssemblerRules.requiredVis(1,Float.NaN),"Reject nonfinite cost");
        System.out.println("THE_ASSEMBLER_RULES_PASS "+checks);
    }
    private AssemblerRulesTest(){}
}
