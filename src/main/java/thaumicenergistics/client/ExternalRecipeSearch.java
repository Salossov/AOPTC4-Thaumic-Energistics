package thaumicenergistics.client;

import appeng.integration.abstraction.ItemListMod;
import java.lang.reflect.Method;
import net.neoforged.fml.ModList;

/** AE2 handles EMI/REI; the existing Compact integration provides the optional JEI hook. */
final class ExternalRecipeSearch {
    private static Method compactSearch;
    static boolean publish(String text){
        if(ItemListMod.isEnabled()){ItemListMod.setSearchText(text);return true;}
        if(!ModList.get().isLoaded("thaumcraft_viewers"))return false;
        try{
            if(compactSearch==null)compactSearch=Class.forName("thaumcraft.viewers.jei.JeiPlugin").getMethod("setTerminalSearchText",String.class);
            return Boolean.TRUE.equals(compactSearch.invoke(null,text));
        }catch(ReflectiveOperationException|LinkageError unavailable){return false;}
    }
    private ExternalRecipeSearch(){}
}
