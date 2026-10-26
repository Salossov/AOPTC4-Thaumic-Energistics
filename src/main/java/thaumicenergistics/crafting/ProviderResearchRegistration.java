package thaumicenergistics.crafting;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import thaumcraft.api.addon.*;
import thaumcraft.api.research.ResearchDefinition;
import thaumcraft.api.world.ContentRules;

/** The original allowMirrors switch selects both the visible link and recipe. */
public final class ProviderResearchRegistration {
    private ProviderResearchRegistration(){}
    public static void initialize(){AddonRegistrationEvent.BUS.addListener(ProviderResearchRegistration::register);}
    private static void register(AddonRegistrationEvent event){
        boolean mirrors=ContentRules.mirrorsEnabled();
        AddonRegistry.research(read(mirrors?"pseudo_mirror":"pseudo_jarlabel"));
        AddonRegistry.research(read(mirrors?"teinfprov":"teinfprov_nomirrors"),
                () -> thaumicenergistics.config.ContentFeature.enabled("infusion_provider"));
    }
    private static ResearchDefinition read(String name){
        String path="/data/thaumicenergistics/research_templates/"+name+".json";
        try(var stream=ProviderResearchRegistration.class.getResourceAsStream(path)){
            if(stream==null)throw new IllegalStateException("Missing research template "+path);
            var json=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject();
            var aspects=new LinkedHashMap<String,Integer>();json.getAsJsonObject("aspects").entrySet().forEach(entry->aspects.put(entry.getKey(),entry.getValue().getAsInt()));
            return new ResearchDefinition(text(json,"key"),text(json,"category"),strings(json,"parents"),aspects,
                    number(json,"complexity"),flag(json,"auto_unlock"),flag(json,"concealed"),number(json,"column"),number(json,"row"),
                    strings(json,"text_pages"),strings(json,"recipe_pages"),text(json,"icon"),flag(json,"virtual"),flag(json,"round"),flag(json,"stub"),flag(json,"secondary"),
                    strings(json,"siblings"),strings(json,"page_order"),flag(json,"hidden"),number(json,"warp"),strings(json,"item_triggers"),strings(json,"entity_triggers"),strings(json,"aspect_triggers"),
                    strings(json,"hidden_parents"),Map.of(),flag(json,"special"),Map.of(),text(json,"linked_research"),flag(json,"lost"));
        }catch(IOException failure){throw new UncheckedIOException("Cannot read research template "+path,failure);}
    }
    private static String text(JsonObject json,String key){return json.has(key)?json.get(key).getAsString():"";}
    private static int number(JsonObject json,String key){return json.has(key)?json.get(key).getAsInt():0;}
    private static boolean flag(JsonObject json,String key){return json.has(key)&&json.get(key).getAsBoolean();}
    private static List<String> strings(JsonObject json,String key){
        if(!json.has(key))return List.of();var result=new ArrayList<String>();for(var entry:json.getAsJsonArray(key))result.add(entry.getAsString());return List.copyOf(result);
    }
}
