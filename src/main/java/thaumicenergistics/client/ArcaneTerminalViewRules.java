package thaumicenergistics.client;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.config.ViewItems;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Presentation rules adapted from the RV3 ItemRepo and ItemSorters contracts. */
final class ArcaneTerminalViewRules {
    record Search(Pattern expression,boolean modOnly){
        boolean matches(String name,String mod,Iterable<String> tooltip){
            if(expression.matcher((modOnly?mod:name).toLowerCase(Locale.ROOT)).find())return true;
            for(var line:tooltip)if(expression.matcher(line).find())return true;
            return false;
        }
    }
    static Search search(String text){
        String query=text.trim().toLowerCase(Locale.ROOT);boolean mod=query.startsWith("@");
        if(mod)query=query.substring(1);
        Pattern expression;
        try{expression=Pattern.compile(query,Pattern.CASE_INSENSITIVE);}
        catch(PatternSyntaxException invalid){expression=Pattern.compile(Pattern.quote(query),Pattern.CASE_INSENSITIVE);}
        return new Search(expression,mod);
    }
    static int compare(SortOrder sort,SortDir direction,ViewItems view,
                       String firstName,String secondName,String firstMod,String secondMod,long firstAmount,long secondAmount){
        int result=switch(sort){
            case AMOUNT->view==ViewItems.CRAFTABLE?0:Long.compare(secondAmount,firstAmount);
            case MOD->{int mod=secondMod.compareToIgnoreCase(firstMod);yield mod==0?secondName.compareToIgnoreCase(firstName):mod;}
            default->firstName.compareToIgnoreCase(secondName);
        };
        return direction==SortDir.DESCENDING?-result:result;
    }
    /** Source order: AUTOSEARCH, MANUAL_SEARCH, NEI_AUTOSEARCH, NEI_MANUAL_SEARCH. */
    static int searchMode(boolean focus,boolean external){return (external?2:0)+(focus?0:1);}
    static int rotateSearchMode(int current,boolean backwards){return Math.floorMod(current+(backwards?-1:1),4);}
    static float costAlpha(long millis){return .25F+.5F*Math.abs((float)((millis/.5D)%2000)-1000)/1000;}
    private ArcaneTerminalViewRules(){}
}
