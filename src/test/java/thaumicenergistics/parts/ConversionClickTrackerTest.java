package thaumicenergistics.parts;

import java.util.UUID;

/** Release timing/identity boundaries, independent of container API approval. */
public final class ConversionClickTrackerTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        var first=UUID.randomUUID();var second=UUID.randomUUID();
        var tracker=new ConversionClickTracker<String>();
        check(tracker.consume(first,100)==null,"fresh monitor has no double click");
        tracker.deposited(first,100,"aqua");check(tracker.consume(first,140).equals("aqua"),"fortieth tick is included");
        check(tracker.consume(first,140)==null,"a third click cannot repeat the consumed inventory sweep");
        tracker.deposited(first,100,"ignis");check(tracker.consume(first,141)==null,"forty-first tick is a new single deposit");
        tracker.deposited(first,141,"terra");check(tracker.consume(first,142).equals("terra"),"successful later deposit replaces stale aspect");
        tracker.deposited(first,200,"aer");check(tracker.consume(second,201)==null,"different player cannot sweep original player's aspect");
        check(tracker.consume(first,202).equals("aer"),"failed other-player check does not consume original click");
        tracker.deposited(first,300,"ordo");tracker.deposited(second,301,"perditio");
        check(tracker.consume(first,302)==null,"another successful depositor owns the new latch");
        check(tracker.consume(second,303).equals("perditio"),"batch selects the aspect from that depositor's successful transfer");
        tracker.deposited(first,Integer.MAX_VALUE-10,"aqua");
        check(tracker.consume(first,Integer.MIN_VALUE+10).equals("aqua"),"signed server tick rollover preserves recent click");
        var otherMonitor=new ConversionClickTracker<String>();tracker.deposited(first,500,"ignis");
        check(otherMonitor.consume(first,501)==null,"double-click state belongs to one monitor, not a global player cache");
        check(tracker.consume(first,501).equals("ignis"),"other monitor does not steal first monitor's latch");
        System.out.println("THE_CONVERSION_CLICK_RULES_PASS checks="+checks);
    }
}
