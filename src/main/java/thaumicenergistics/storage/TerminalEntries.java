package thaumicenergistics.storage;

import appeng.api.config.ViewItems;
import java.util.*;

/** Original client filtering/comparison, including the release's narrowing amount subtraction. */
public final class TerminalEntries {
    public static List<TerminalEntry> visible(List<TerminalEntry> entries,int sort,ViewItems view,String search){
        String query=search.trim().toLowerCase(Locale.ROOT);var result=new ArrayList<TerminalEntry>();
        for(var entry:entries){var aspect=entry.key().aspect();if(aspect==null)continue;
            if(view==ViewItems.CRAFTABLE&&!entry.craftable()||view==ViewItems.STORED&&entry.amount()==0)continue;
            if(!query.isEmpty()&&!aspect.getName().contains(query)&&!aspect.getTag().contains(query))continue;
            result.add(entry);
        }
        result.sort((a,b)->{
            int amount=sort==1?(int)(b.amount()-a.amount()):0;
            return amount!=0?amount:a.key().aspect().getName().compareTo(b.key().aspect().getName());
        });return result;
    }
    private TerminalEntries(){}
}
