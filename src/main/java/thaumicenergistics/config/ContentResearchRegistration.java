package thaumicenergistics.config;

import com.google.gson.*;
import com.mojang.serialization.MapCodec;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import thaumcraft.api.addon.AddonRegistrationEvent;
import thaumcraft.api.addon.AddonRegistry;
import thaumcraft.api.research.ResearchDefinition;
import thaumicenergistics.ThaumicEnergistics;

/** Uses the same public, conditional template registration as the existing providers/extras. */
public final class ContentResearchRegistration {
    private static final List<String> TEMPLATES = List.of("testorage", "tearcaneterm", "tevisint",
            "tearcaneassembler", "teknowledgeinscriber", "tedistillationpatternencoder", "teessprov",
            "teessvibcmbr", "tegolembackpack", "teio", "teessterm");
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, ThaumicEnergistics.ID);
    static { CONDITIONS.register("content_feature", () -> ContentFeatureCondition.CODEC); }

    public static void initialize(IEventBus bus) {
        CONDITIONS.register(bus);
        AddonRegistrationEvent.BUS.addListener((AddonRegistrationEvent event) -> {
            for (String name : TEMPLATES)
                AddonRegistry.research(read(name), () -> ContentFeature.researchEnabled(name));
        });
    }

    public static ResearchDefinition read(String name) {
        String path = "/data/thaumicenergistics/research_templates/" + name + ".json";
        try (var stream = ContentResearchRegistration.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing research template " + path);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            adapt(name, json);
            var aspects = new LinkedHashMap<String, Integer>();
            if (json.has("aspects")) json.getAsJsonObject("aspects").entrySet().forEach(e -> aspects.put(e.getKey(), e.getValue().getAsInt()));
            var requirements = new LinkedHashMap<String, String>();
            if (json.has("page_requirements")) json.getAsJsonObject("page_requirements").entrySet().forEach(e -> requirements.put(e.getKey(), e.getValue().getAsString()));
            var groups = new LinkedHashMap<String, List<String>>();
            if (json.has("recipe_groups")) json.getAsJsonObject("recipe_groups").entrySet().forEach(e -> groups.put(e.getKey(), values(e.getValue().getAsJsonArray())));
            return new ResearchDefinition(text(json,"key"),text(json,"category"),strings(json,"parents"),aspects,
                    number(json,"complexity"),flag(json,"auto_unlock"),flag(json,"concealed"),number(json,"column"),number(json,"row"),
                    strings(json,"text_pages"),strings(json,"recipe_pages"),text(json,"icon"),flag(json,"virtual"),flag(json,"round"),flag(json,"stub"),flag(json,"secondary"),
                    strings(json,"siblings"),strings(json,"page_order"),flag(json,"hidden"),number(json,"warp"),strings(json,"item_triggers"),strings(json,"entity_triggers"),strings(json,"aspect_triggers"),
                    strings(json,"hidden_parents"),requirements,flag(json,"special"),groups,text(json,"linked_research"),flag(json,"lost"));
        } catch (IOException failure) { throw new UncheckedIOException("Cannot read research template " + path, failure); }
    }

    private static void adapt(String name, JsonObject json) {
        if (name.equals("tevisint"))
            replace(json, "parents", "thaumicenergistics:tearcaneterm", ContentFeature.firstAvailableParent("tearcaneterm"));
        if (name.equals("tearcaneassembler")) {
            replace(json, "parents", "thaumicenergistics:tevisint", ContentFeature.firstAvailableParent("tevisint"));
            replace(json, "hidden_parents", "thaumicenergistics:tearcaneterm", ContentFeature.firstAvailableParent("tearcaneterm"));
        }
        if (name.equals("teio") && !ContentFeature.enabled("io_buses")) {
            removePage(json, "thaumicenergistics.research_page.TEIO.1");
            removePage(json, "thaumicenergistics:essentia_import_bus");
            removePage(json, "thaumicenergistics:essentia_export_bus");
        }
        if (name.equals("teessterm") && !ContentFeature.enabled("wireless_essentia_terminal"))
            removePage(json, "thaumicenergistics:wireless_essentia_terminal");
    }

    private static void replace(JsonObject json, String field, String from, String to) {
        var array = new JsonArray();
        for (String value : strings(json, field)) array.add(value.equals(from) ? to : value);
        json.add(field, array);
    }
    private static void removePage(JsonObject json, String page) {
        for (String field : List.of("text_pages", "recipe_pages", "page_order")) {
            if (!json.has(field)) continue;
            var array = new JsonArray();
            for (String value : strings(json, field)) if (!value.equals(page)) array.add(value);
            json.add(field, array);
        }
    }
    private static String text(JsonObject json,String key){return json.has(key)?json.get(key).getAsString():"";}
    private static int number(JsonObject json,String key){return json.has(key)?json.get(key).getAsInt():0;}
    private static boolean flag(JsonObject json,String key){return json.has(key)&&json.get(key).getAsBoolean();}
    private static List<String> strings(JsonObject json,String key){return json.has(key)?values(json.getAsJsonArray(key)):List.of();}
    private static List<String> values(JsonArray values){var result=new ArrayList<String>();values.forEach(value->result.add(value.getAsString()));return List.copyOf(result);}
    private ContentResearchRegistration() { }
}
