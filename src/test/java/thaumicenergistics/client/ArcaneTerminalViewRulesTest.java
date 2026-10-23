package thaumicenergistics.client;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import java.util.List;

/** Source-backed cases; intentionally deferred to the final consolidated check stage. */
public final class ArcaneTerminalViewRulesTest {
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static int compare(SortOrder sort,SortDir direction,ViewItems view,String first,String second,String mod1,String mod2,long amount1,long amount2){
        return ArcaneTerminalViewRules.compare(sort,direction,view,first,second,mod1,mod2,amount1,amount2);
    }
    public static void main(String[] args){
        check(ArcaneTerminalViewRules.search("  ^iron.*ingot$  ").matches("Iron Ingot","minecraft",List.of()),"Regular expression and whitespace normalization");
        check(!ArcaneTerminalViewRules.search("^iron.*ingot$").matches("Iron Nugget","minecraft",List.of()),"Regex anchors distinguish recipes");
        check(ArcaneTerminalViewRules.search("[").matches("Crystal [Charged]","ae2",List.of()),"Invalid regex becomes literal text");
        check(!ArcaneTerminalViewRules.search("[").matches("Crystal","ae2",List.of()),"Invalid regex does not match everything");
        check(ArcaneTerminalViewRules.search("@^ae2$").matches("Quartz","ae2",List.of()),"Mod search supports expressions");
        check(!ArcaneTerminalViewRules.search("@^ae2$").matches("ae2 named item","minecraft",List.of()),"Mod prefix does not search item name directly");
        check(ArcaneTerminalViewRules.search("@aer").matches("Crystal","thaumcraft",List.of("Aer infused")),"RV3 tooltip fallback also applies to mod searches");
        check(ArcaneTerminalViewRules.search("arcane").matches("Crystal","thaumcraft",List.of("ARCANE ingredient")),"Tooltip search remains case insensitive");
        check(ArcaneTerminalViewRules.search("").matches("Anything","any",List.of()),"Empty search accepts items");
        check(compare(SortOrder.NAME,SortDir.ASCENDING,ViewItems.ALL,"Alpha","beta","z","a",1,100)<0,"Name sort ignores counts and case");
        check(compare(SortOrder.NAME,SortDir.DESCENDING,ViewItems.ALL,"Alpha","beta","z","a",1,100)>0,"Descending name sort");
        check(compare(SortOrder.MOD,SortDir.ASCENDING,ViewItems.ALL,"Alpha","beta","zmod","amod",1,100)<0,"Original ascending mod sort uses reverse mod order");
        check(compare(SortOrder.MOD,SortDir.ASCENDING,ViewItems.ALL,"Alpha","beta","same","same",1,100)>0,"Original mod tie uses reverse display name");
        check(compare(SortOrder.MOD,SortDir.DESCENDING,ViewItems.ALL,"Alpha","beta","same","same",1,100)<0,"Descending mod tie reverses too");
        check(compare(SortOrder.AMOUNT,SortDir.ASCENDING,ViewItems.ALL,"Alpha","beta","z","a",Long.MAX_VALUE,1)<0,"Long counts do not narrow or subtract");
        check(compare(SortOrder.AMOUNT,SortDir.DESCENDING,ViewItems.ALL,"Alpha","beta","z","a",Long.MAX_VALUE,1)>0,"Ascending numeric direction flips");
        check(compare(SortOrder.AMOUNT,SortDir.ASCENDING,ViewItems.ALL,"Alpha","beta","z","a",8,8)==0,"Equal amount preserves prior order");
        check(compare(SortOrder.AMOUNT,SortDir.ASCENDING,ViewItems.CRAFTABLE,"Alpha","beta","z","a",100,1)==0,"Craftable-only view sorts zero displayed counts");
        for(int mode=0;mode<4;mode++){
            check(ArcaneTerminalViewRules.searchMode(mode%2==0,mode>=2)==mode,"Four native flag combinations map independently");
            check(ArcaneTerminalViewRules.rotateSearchMode(ArcaneTerminalViewRules.rotateSearchMode(mode,false),true)==mode,"Reverse mode rotation");
        }
        check(ArcaneTerminalViewRules.rotateSearchMode(3,false)==0,"Forward search mode wraps");
        check(ArcaneTerminalViewRules.rotateSearchMode(0,true)==3,"Reverse search mode wraps");
        check(ArcaneTerminalViewRules.costAlpha(0)==.75F,"Source blink high alpha");
        check(ArcaneTerminalViewRules.costAlpha(250)==.5F,"Source blink linear midpoint");
        check(ArcaneTerminalViewRules.costAlpha(500)==.25F,"Source blink low alpha");
        check(ArcaneTerminalViewRules.costAlpha(1000)==.75F,"Source blink period is one second");
        System.out.println("THE_ARCANE_TERMINAL_VIEW_RULES_PASS "+checks);
    }
    private ArcaneTerminalViewRulesTest(){}
}
