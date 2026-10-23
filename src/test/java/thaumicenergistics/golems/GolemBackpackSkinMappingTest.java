package thaumicenergistics.golems;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Deferred unit test verifying Golem Backpack skin mappings and 10 skin constants.
 * (DO NOT RUN per prompt constraints: deferred for future test suite run).
 */
public class GolemBackpackSkinMappingTest {
    @Test
    public void testSkinEnumIntegrity() {
        Assertions.assertEquals(10, GolemBackpackSkin.VALUES.length);
        Assertions.assertEquals(GolemBackpackSkin.THAUMIUM, GolemBackpackSkin.byId(0));
        Assertions.assertEquals(GolemBackpackSkin.STONE, GolemBackpackSkin.byId(1));
        Assertions.assertEquals(GolemBackpackSkin.STRAW, GolemBackpackSkin.byId(2));
        Assertions.assertEquals(GolemBackpackSkin.GREATWOOD, GolemBackpackSkin.byId(3));
        Assertions.assertEquals(GolemBackpackSkin.FLESH, GolemBackpackSkin.byId(4));
        Assertions.assertEquals(GolemBackpackSkin.CLAY, GolemBackpackSkin.byId(5));
        Assertions.assertEquals(GolemBackpackSkin.IRON, GolemBackpackSkin.byId(6));
        Assertions.assertEquals(GolemBackpackSkin.TALLOW, GolemBackpackSkin.byId(7));
        Assertions.assertEquals(GolemBackpackSkin.GOLD, GolemBackpackSkin.byId(8));
        Assertions.assertEquals(GolemBackpackSkin.DIAMOND, GolemBackpackSkin.byId(9));
    }

    @Test
    public void testMaterialStringMapping() {
        Assertions.assertEquals(GolemBackpackSkin.STONE, GolemBackpackSkin.fromMaterialName("stone"));
        Assertions.assertEquals(GolemBackpackSkin.STRAW, GolemBackpackSkin.fromMaterialName("straw"));
        Assertions.assertEquals(GolemBackpackSkin.GREATWOOD, GolemBackpackSkin.fromMaterialName("wood"));
        Assertions.assertEquals(GolemBackpackSkin.GREATWOOD, GolemBackpackSkin.fromMaterialName("greatwood"));
        Assertions.assertEquals(GolemBackpackSkin.FLESH, GolemBackpackSkin.fromMaterialName("flesh"));
        Assertions.assertEquals(GolemBackpackSkin.CLAY, GolemBackpackSkin.fromMaterialName("clay"));
        Assertions.assertEquals(GolemBackpackSkin.IRON, GolemBackpackSkin.fromMaterialName("iron"));
        Assertions.assertEquals(GolemBackpackSkin.TALLOW, GolemBackpackSkin.fromMaterialName("tallow"));
        Assertions.assertEquals(GolemBackpackSkin.GOLD, GolemBackpackSkin.fromMaterialName("gold"));
        Assertions.assertEquals(GolemBackpackSkin.DIAMOND, GolemBackpackSkin.fromMaterialName("diamond"));
        Assertions.assertEquals(GolemBackpackSkin.THAUMIUM, GolemBackpackSkin.fromMaterialName("unknown"));
    }

    @Test
    public void testTexturePathsAreLowercase() {
        for (GolemBackpackSkin skin : GolemBackpackSkin.VALUES) {
            String path = skin.getTextureLocation().getPath();
            Assertions.assertEquals(path.toLowerCase(java.util.Locale.ROOT), path, "Texture path must be lower case");
            Assertions.assertTrue(path.startsWith("textures/models/golem_backpack/"), "Must be in golem_backpack directory");
        }
    }
}
