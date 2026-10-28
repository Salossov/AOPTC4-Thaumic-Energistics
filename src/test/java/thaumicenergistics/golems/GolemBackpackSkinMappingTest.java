package thaumicenergistics.golems;

import static thaumicenergistics.golems.GolemRulesTest.*;

/**
 * Deferred unit test verifying Golem Backpack skin mappings and 10 skin constants.
 * Executed by the golemRulesTest main harness during check.
 */
public class GolemBackpackSkinMappingTest {
    public void testSkinEnumIntegrity() {
        equal(10, GolemBackpackSkin.VALUES.length);
        equal(GolemBackpackSkin.THAUMIUM, GolemBackpackSkin.byId(0));
        equal(GolemBackpackSkin.STONE, GolemBackpackSkin.byId(1));
        equal(GolemBackpackSkin.STRAW, GolemBackpackSkin.byId(2));
        equal(GolemBackpackSkin.GREATWOOD, GolemBackpackSkin.byId(3));
        equal(GolemBackpackSkin.FLESH, GolemBackpackSkin.byId(4));
        equal(GolemBackpackSkin.CLAY, GolemBackpackSkin.byId(5));
        equal(GolemBackpackSkin.IRON, GolemBackpackSkin.byId(6));
        equal(GolemBackpackSkin.TALLOW, GolemBackpackSkin.byId(7));
        equal(GolemBackpackSkin.GOLD, GolemBackpackSkin.byId(8));
        equal(GolemBackpackSkin.DIAMOND, GolemBackpackSkin.byId(9));
    }

    public void testMaterialStringMapping() {
        equal(GolemBackpackSkin.STONE, GolemBackpackSkin.fromMaterialName("stone"));
        equal(GolemBackpackSkin.STRAW, GolemBackpackSkin.fromMaterialName("straw"));
        equal(GolemBackpackSkin.GREATWOOD, GolemBackpackSkin.fromMaterialName("wood"));
        equal(GolemBackpackSkin.GREATWOOD, GolemBackpackSkin.fromMaterialName("greatwood"));
        equal(GolemBackpackSkin.FLESH, GolemBackpackSkin.fromMaterialName("flesh"));
        equal(GolemBackpackSkin.CLAY, GolemBackpackSkin.fromMaterialName("clay"));
        equal(GolemBackpackSkin.IRON, GolemBackpackSkin.fromMaterialName("iron"));
        equal(GolemBackpackSkin.TALLOW, GolemBackpackSkin.fromMaterialName("tallow"));
        equal(GolemBackpackSkin.GOLD, GolemBackpackSkin.fromMaterialName("gold"));
        equal(GolemBackpackSkin.DIAMOND, GolemBackpackSkin.fromMaterialName("diamond"));
        equal(GolemBackpackSkin.THAUMIUM, GolemBackpackSkin.fromMaterialName("unknown"));
    }

    public void testTexturePathsAreLowercase() {
        for (GolemBackpackSkin skin : GolemBackpackSkin.VALUES) {
            String path = skin.getTextureLocation().getPath();
            equal(path.toLowerCase(java.util.Locale.ROOT), path, "Texture path must be lower case");
            require(path.startsWith("textures/models/golem_backpack/"), "Must be in golem_backpack directory");
        }
    }
}
