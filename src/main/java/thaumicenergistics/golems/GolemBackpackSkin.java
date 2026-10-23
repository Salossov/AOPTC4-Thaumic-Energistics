package thaumicenergistics.golems;

import appeng.api.implementations.items.IFacadeItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import thaumicenergistics.ThaumicEnergistics;

/**
 * All 10 golem backpack visual skins matching 1.7.10 BackpackSkins.
 * Maps to lower-case texture locations in assets/thaumicenergistics/textures/models/golem_backpack/.
 */
public enum GolemBackpackSkin {
    THAUMIUM(0, "thaumium", "thaum.png"),
    STONE(1, "stone", "stone.png"),
    STRAW(2, "straw", "straw.png"),
    GREATWOOD(3, "wood", "wood.png"),
    FLESH(4, "flesh", "flesh.png"),
    CLAY(5, "clay", "clay.png"),
    IRON(6, "iron", "iron.png"),
    TALLOW(7, "tallow", "tallow.png"),
    GOLD(8, "gold", "gold.png"),
    DIAMOND(9, "diamond", "diamond.png");

    public static final GolemBackpackSkin[] VALUES = values();

    private final int id;
    private final String key;
    private final String textureFilename;
    private ResourceLocation textureLocation;

    GolemBackpackSkin(int id, String key, String textureFilename) {
        this.id = id;
        this.key = key;
        this.textureFilename = textureFilename;
    }

    public int getId() {
        return id;
    }

    public String getKey() {
        return key;
    }

    public ResourceLocation getTextureLocation() {
        if (this.textureLocation == null) {
            this.textureLocation = ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "textures/models/golem_backpack/" + this.textureFilename);
        }
        return this.textureLocation;
    }

    public static GolemBackpackSkin byId(int id) {
        if (id >= 0 && id < VALUES.length) {
            return VALUES[id];
        }
        return THAUMIUM;
    }

    public static GolemBackpackSkin fromMaterialName(String material) {
        if (material == null) {
            return THAUMIUM;
        }
        String clean = material.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (clean) {
            case "stone" -> STONE;
            case "straw" -> STRAW;
            case "wood", "greatwood" -> GREATWOOD;
            case "flesh" -> FLESH;
            case "clay" -> CLAY;
            case "iron" -> IRON;
            case "tallow" -> TALLOW;
            case "gold" -> GOLD;
            case "diamond" -> DIAMOND;
            default -> THAUMIUM;
        };
    }

    /**
     * Extracts backpack skin from an AE2 Facade item based on the exact 1.7.10 whitelist:
     * - Vanilla: stone, hay_block, bricks, iron_block, gold_block, diamond_block
     * - Thaumcraft: thaumium_block, tallow_block, greatwood_log, tainted_flesh
     * Non-facade items and unauthorized blocks strictly return null.
     */
    public static GolemBackpackSkin fromFacade(ItemStack facade) {
        if (facade == null || facade.isEmpty()) {
            return null;
        }
        if (!(facade.getItem() instanceof IFacadeItem facadeItem)) {
            return null;
        }

        BlockState state = facadeItem.getTextureBlockState(facade);
        if (state == null) {
            return null;
        }
        Block block = state.getBlock();
        if (block == null || block == Blocks.AIR) {
            return null;
        }

        if (block == Blocks.STONE) return STONE;
        if (block == Blocks.HAY_BLOCK) return STRAW;
        if (block == Blocks.BRICKS) return CLAY;
        if (block == Blocks.IRON_BLOCK) return IRON;
        if (block == Blocks.GOLD_BLOCK) return GOLD;
        if (block == Blocks.DIAMOND_BLOCK) return DIAMOND;

        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        if ("thaumcraft".equals(id.getNamespace())) {
            String path = id.getPath();
            if ("thaumium_block".equals(path)) return THAUMIUM;
            if ("tallow_block".equals(path)) return TALLOW;
            if ("greatwood_log".equals(path)) return GREATWOOD;
            if ("tainted_flesh".equals(path)) return FLESH;
        }

        return null;
    }
}
