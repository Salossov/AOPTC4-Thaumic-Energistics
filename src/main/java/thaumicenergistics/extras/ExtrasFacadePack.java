package thaumicenergistics.extras;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import thaumcraft.api.world.ContentRules;

/** One additive server tag, evaluated from the original options on every data reload. */
final class ExtrasFacadePack extends AbstractPackResources {
    private static final ResourceLocation TAG = ResourceLocation.fromNamespaceAndPath(
            "ae2", "tags/block/whitelisted/facades.json");
    // Exact available counterparts of the release whitelist; absent entries are optional.
    private static final List<String> BLOCKS = List.of(
            "amber_block", "amber_bricks", "warded_glass", "obsidian_tile", "thaumium_block",
            "tallow_block", "arcane_stone", "arcane_stone_bricks", "ancient_stone",
            "greatwood_planks", "silverwood_planks", "fibrous_taint", "tainted_flesh");

    private ExtrasFacadePack(PackLocationInfo location) {
        super(location);
    }

    static void register(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) return;
        event.addRepositorySource(consumer -> {
            var location = new PackLocationInfo("thaumicenergistics/tc_facades",
                    Component.literal("Thaumic Energistics facades"), PackSource.BUILT_IN, Optional.empty());
            var supplier = new Pack.ResourcesSupplier() {
                @Override public PackResources openPrimary(PackLocationInfo info) {
                    return new ExtrasFacadePack(info);
                }
                @Override public PackResources openFull(PackLocationInfo info, Pack.Metadata metadata) {
                    return new ExtrasFacadePack(info);
                }
            };
            var pack = Pack.readMetaAndCreate(location, supplier, PackType.SERVER_DATA,
                    new PackSelectionConfig(true, Pack.Position.BOTTOM, true));
            if (pack == null) throw new IllegalStateException("Unable to create facade data pack");
            consumer.accept(pack);
        });
    }

    @Override
    public IoSupplier<InputStream> getRootResource(String... path) {
        if (path.length != 1 || !"pack.mcmeta".equals(path[0])) return null;
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA);
        byte[] metadata = ("{\"pack\":{\"pack_format\":" + format
                + ",\"description\":\"Thaumic Energistics facades\"}}").getBytes(StandardCharsets.UTF_8);
        return () -> new ByteArrayInputStream(metadata);
    }

    @Override
    public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
        if (type != PackType.SERVER_DATA || !TAG.equals(id)) return null;
        return () -> new ByteArrayInputStream(facadeTag(ExtrasConfig.forceTCFacades(), ContentRules.mirrorsEnabled()));
    }

    static byte[] facadeTag(boolean enabled, boolean mirrors) {
        var json = new JsonObject();
        json.addProperty("replace", false);
        var values = new JsonArray();
        if (enabled) {
            for (String block : BLOCKS) {
                if ("warded_glass".equals(block) && !mirrors) continue;
                var entry = new JsonObject();
                entry.addProperty("id", "thaumcraft:" + block);
                entry.addProperty("required", false);
                values.add(entry);
            }
        }
        json.add("values", values);
        return json.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        if (type == PackType.SERVER_DATA && TAG.getNamespace().equals(namespace)
                && (path.isEmpty() || TAG.getPath().startsWith(path.endsWith("/") ? path : path + "/"))) {
            output.accept(TAG, getResource(type, TAG));
        }
    }

    @Override
    public Set<String> getNamespaces(PackType type) {
        return type == PackType.SERVER_DATA ? Set.of("ae2") : Set.of();
    }

    @Override public void close() {}
}
