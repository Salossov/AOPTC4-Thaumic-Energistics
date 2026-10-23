package thaumicenergistics.extras;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.MapCodec;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import thaumcraft.api.addon.AddonRegistrationEvent;
import thaumcraft.api.addon.AddonRegistry;
import thaumcraft.api.research.ResearchDefinition;
import thaumcraft.api.wands.FocusBehaviors;
import thaumicenergistics.ThaumicEnergistics;

/**
 * Autonomous registrar for the batch-2 extras (wrench focus, quartz duplication,
 * Thaumcraft facades). One line in the mod constructor calls
 * {@link #initialize(IEventBus)}; everything else stays inside this package.
 * Fixed IDs owned by this package: thaumicenergistics:focus_ae_wrench,
 * thaumicenergistics:extras_feature, research keys thaumicenergistics:tefocuswrench /
 * :tecertusdupe / :pseudo_focusfire / :pseudo_alchemicalduplication and the
 * ae2:whitelisted/facades tag entries.
 */
public final class ExtrasRegistrar {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ThaumicEnergistics.ID);
    public static final DeferredItem<FocusAEWrenchItem> FOCUS_AE_WRENCH =
            ITEMS.register("focus_ae_wrench", FocusAEWrenchItem::new);
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, ThaumicEnergistics.ID);
    private static final DeferredHolder<MapCodec<? extends ICondition>, MapCodec<ExtrasFeatureCondition>> EXTRAS_FEATURE =
            CONDITIONS.register("extras_feature", () -> ExtrasFeatureCondition.CODEC);
    private static final ResourceKey<CreativeModeTab> ADDON_TAB =
            ResourceKey.create(Registries.CREATIVE_MODE_TAB, ThaumicEnergistics.id("main"));

    /** Called once from the mod constructor, mirroring ProviderResearchRegistration.initialize(). */
    public static void initialize(IEventBus modBus) {
        ITEMS.register(modBus);
        CONDITIONS.register(modBus);
        modBus.addListener(ExtrasFacadePack::register);
        AddonRegistrationEvent.BUS.addListener(ExtrasRegistrar::registerAddon);
        modBus.addListener((net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == ADDON_TAB) {
                event.accept(FOCUS_AE_WRENCH);
            }
        });
    }

    /**
     * Startup registration on the addon bus: the focus behavior with its
     * attached wand/staff models, the two pseudo parents (linked_research) and
     * the two ordinary researches behind the config seam.
     */
    private static void registerAddon(AddonRegistrationEvent event) {
        var matrixProbe = thaumcraft.api.crafting.InfusionMatrixApi.probe();
        if (matrixProbe != null) {
            FocusAEWrenchInteraction.installMatrixProbe(new FocusAEWrenchInteraction.MatrixProbe() {
                @Override public boolean isMatrix(net.minecraft.world.level.block.entity.BlockEntity tile) {
                    return matrixProbe.isMatrix(tile);
                }
                @Override public boolean requestSymmetry(net.minecraft.server.level.ServerPlayer player,
                        net.minecraft.world.level.block.entity.BlockEntity tile) {
                    var symmetry = matrixProbe.requestSymmetry(player, tile);
                    if (symmetry.isEmpty()) return false;
                    player.displayClientMessage(net.minecraft.network.chat.Component.literal(
                            "Symmetry: " + symmetry.getAsInt()), true);
                    return true;
                }
            });
        }
        FocusBehaviors.register(FOCUS_AE_WRENCH.get(), FocusAEWrenchBehavior.INSTANCE,
                ThaumicEnergistics.id("wand/focus_ae_wrench"), null, ThaumicEnergistics.id("staff/focus_ae_wrench"));
        AddonRegistry.research(read("pseudo_focusfire"));
        AddonRegistry.research(read("pseudo_alchemicalduplication"));
        AddonRegistry.research(read("tefocuswrench"), ExtrasConfig::wrenchFocus);
        AddonRegistry.research(read("tecertusdupe"), ExtrasConfig::certusDupe);
    }

    private static ResearchDefinition read(String name) {
        String path = "/data/thaumicenergistics/research_templates/" + name + ".json";
        try (var stream = ExtrasRegistrar.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing extras research template " + path);
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            var aspects = new LinkedHashMap<String, Integer>();
            if (json.has("aspects")) {
                json.getAsJsonObject("aspects").entrySet().forEach(entry -> aspects.put(entry.getKey(), entry.getValue().getAsInt()));
            }
            return new ResearchDefinition(text(json, "key"), text(json, "category"), strings(json, "parents"), aspects,
                    number(json, "complexity"), flag(json, "auto_unlock"), flag(json, "concealed"), number(json, "column"), number(json, "row"),
                    strings(json, "text_pages"), strings(json, "recipe_pages"), text(json, "icon"), flag(json, "virtual"), flag(json, "round"), flag(json, "stub"),
                    flag(json, "secondary"), strings(json, "siblings"), strings(json, "page_order"), flag(json, "hidden"), number(json, "warp"),
                    strings(json, "item_triggers"), strings(json, "entity_triggers"), strings(json, "aspect_triggers"),
                    strings(json, "hidden_parents"), Map.of(), flag(json, "special"), Map.of(), text(json, "linked_research"), flag(json, "lost"));
        } catch (java.io.IOException failure) {
            throw new java.io.UncheckedIOException("Cannot read extras research template " + path, failure);
        }
    }

    private static String text(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsString() : "";
    }

    private static int number(JsonObject json, String key) {
        return json.has(key) ? json.get(key).getAsInt() : 0;
    }

    private static boolean flag(JsonObject json, String key) {
        return json.has(key) && json.get(key).getAsBoolean();
    }

    private static List<String> strings(JsonObject json, String key) {
        if (!json.has(key)) {
            return List.of();
        }
        var result = new ArrayList<String>();
        for (var entry : json.getAsJsonArray(key)) {
            result.add(entry.getAsString());
        }
        return List.copyOf(result);
    }

    private ExtrasRegistrar() {}
}
