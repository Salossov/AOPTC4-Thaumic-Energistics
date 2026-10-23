package thaumicenergistics.verification;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import appeng.api.orientation.IOrientationStrategy;
import appeng.api.parts.IPartHost;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import thaumcraft.api.aspects.Aspects;
import thaumcraft.api.crafting.IInfusionPedestal;
import thaumcraft.api.research.ResearchApi;
import thaumcraft.api.wands.WandApi;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.extras.ExtrasRegistrar;
import thaumicenergistics.extras.FocusAEWrenchInteraction;
import thaumicenergistics.extras.FocusAEWrenchItem;

/**
 * Unrun verification fixtures for the batch-2 extras: the AE wrench focus
 * interaction path (refusals, exact-hit part dismantle, held/shift restore,
 * orientable rotate, pedestal exclusion, exact vis accounting), the facade
 * whitelist data, the crucible recipe data and the four research definitions.
 * Per the implement-everything-first instruction these have NOT been executed;
 * they compile against public APIs and run in the consolidated check stage.
 */
@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class ExtrasFocusNativeTest {
    private static ItemStack item(String id) {
        var key = ResourceLocation.parse(id);
        if (!BuiltInRegistries.ITEM.containsKey(key)) {
            throw new IllegalStateException("Missing fixture item " + id);
        }
        return new ItemStack(BuiltInRegistries.ITEM.get(key));
    }

    private static ServerPlayer fixturePlayer(GameTestHelper helper) {
        var cookie = CommonListenerCookie.createInitial(new com.mojang.authlib.GameProfile(UUID.randomUUID(), "THEExtras"), false);
        var player = new ServerPlayer(helper.getLevel().getServer(), helper.getLevel(), cookie.gameProfile(), cookie.clientInformation());
        player.connection = new ServerGamePacketListenerImpl(player.server, new Connection(PacketFlow.SERVERBOUND), player, cookie) {
            @Override
            public void send(Packet<?> packet) {}
        };
        return player;
    }

    private static ItemStack focusedWand(int fire, int air) {
        var wand = item("thaumcraft:iron_capped_wooden_wand");
        WandApi.set(wand, Aspects.FIRE, fire);
        WandApi.set(wand, Aspects.AIR, air);
        WandApi.focus(wand, new ItemStack(ExtrasRegistrar.FOCUS_AE_WRENCH.get()));
        return wand;
    }

    private static BlockHitResult hit(BlockPos pos, Direction face, double yFraction) {
        return new BlockHitResult(new Vec3(pos.getX() + 0.5, pos.getY() + yFraction, pos.getZ() + 0.5), face, pos, false);
    }

    private static void setCableWithImportBus(GameTestHelper helper, BlockPos cable) {
        var level = helper.getLevel();
        PartHelper.setPart(level, cable, null, null, IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        PartHelper.setPart(level, cable, Direction.UP, null, ThaumicEnergistics.IMPORT_BUS);
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void nativeFocusItemConstants(GameTestHelper helper) {
        var focus = new ItemStack(ExtrasRegistrar.FOCUS_AE_WRENCH.get());
        helper.assertTrue(focus.getItem() instanceof FocusAEWrenchItem focusItem && focusItem.getActivationCooldown(focus) == 1000,
                "original 1000ms activation cooldown (core converts (1000+49)/50=20 cast ticks; ExtrasLeftClickHandler mirrors it)");
        helper.assertTrue(focus.getItem() instanceof FocusAEWrenchItem focusItem2 && focusItem2.getFocusColor(focus) == Aspects.ENERGY.getColor(),
                "original Potentia (Aspect.ENERGY) focus color");
        var cost = FocusAEWrenchItem.castCost();
        helper.assertTrue(cost.size() == 2 && cost.get(Aspects.FIRE) == 10 && cost.get(Aspects.AIR) == 10,
                "release cast cost FIRE 10 + AIR 10 centivis, kept separate from the whole-vis recipe tens");
        helper.assertTrue(((FocusAEWrenchItem) focus.getItem()).getPossibleUpgradesByRank(focus, 1).isEmpty(),
                "original focus has no upgrades");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeFocusRefusals(GameTestHelper helper) {
        var level = helper.getLevel();
        var cable = helper.absolutePos(new BlockPos(3, 2, 3));
        setCableWithImportBus(helper, cable);
        var player = fixturePlayer(helper);
        var charged = focusedWand(1000, 1000);
        var hit = hit(cable, Direction.UP, 0.875);
        // Original precheck order starts with sneaking; nothing else may run without it.
        helper.assertTrue(!FocusAEWrenchInteraction.use(player, charged, hit, FocusAEWrenchInteraction.Action.RIGHT_CLICK),
                "focus refuses a right-click while the player is not sneaking");
        helper.assertTrue(WandApi.stored(charged, Aspects.FIRE) == 1000 && WandApi.stored(charged, Aspects.AIR) == 1000,
                "no vis is simulated away or debited for a refused action");
        // A valid wand with zero charge is refused after the sneak check, like the original wandIfValid.
        player.setShiftKeyDown(true);
        var empty = focusedWand(0, 0);
        helper.assertTrue(!FocusAEWrenchInteraction.use(player, empty, hit, FocusAEWrenchInteraction.Action.RIGHT_CLICK),
                "focus refuses a wand without the FIRE/AIR centivis charge");
        helper.assertTrue(WandApi.stored(empty, Aspects.FIRE) == 0 && WandApi.stored(empty, Aspects.AIR) == 0,
                "uncharged wand remains empty after refusal");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeFocusRightClickDismantlesExactPart(GameTestHelper helper) {
        var level = helper.getLevel();
        var cable = helper.absolutePos(new BlockPos(3, 2, 3));
        setCableWithImportBus(helper, cable);
        var player = fixturePlayer(helper);
        player.setShiftKeyDown(true);
        var wand = focusedWand(1000, 1000);
        var mainBefore = player.getMainHandItem();
        // Aim inside the upper part slice so the AE2 exact-hit selection picks the import bus.
        var hit = hit(cable, Direction.UP, 0.875);
        helper.assertTrue(FocusAEWrenchInteraction.use(player, wand, hit, FocusAEWrenchInteraction.Action.RIGHT_CLICK),
                "sneak right-click through the pseudo wrench pipeline handles the part host");
        var host = (IPartHost) level.getBlockEntity(cable);
        helper.assertTrue(host != null && host.getPart(Direction.UP) == null,
                "only the targeted part is dismantled, never the whole cablebus");
        helper.assertTrue(host != null && host.getPart(null) != null,
                "the center cable and its block remain after a part dismantle");
        helper.assertTrue(ItemStack.isSameItemSameComponents(player.getMainHandItem(), mainBefore),
                "the try/finally restores the held wand with identical item and components");
        helper.assertTrue(player.isShiftKeyDown(), "the finally re-asserts the sneaking state like the original");
        helper.assertTrue(WandApi.stored(wand, Aspects.FIRE) == 1000 && WandApi.stored(wand, Aspects.AIR) == 1000,
                "use() itself only simulates; the right-click debit belongs to the core casting pipeline");
        helper.assertTrue(WandApi.consume(wand, player, FocusAEWrenchItem.castCost(), true),
                "the caller-side commit of the original successful path succeeds");
        helper.assertTrue(WandApi.stored(wand, Aspects.FIRE) == 990 && WandApi.stored(wand, Aspects.AIR) == 990,
                "exactly ten fire and ten air centivis are debited");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeFocusLeftClickRotateAndNoop(GameTestHelper helper) {
        var level = helper.getLevel();
        var charger = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(charger, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:charger")).defaultBlockState());
        var player = fixturePlayer(helper);
        player.setShiftKeyDown(true);
        var wand = focusedWand(1000, 1000);
        var before = level.getBlockState(charger);
        helper.assertTrue(FocusAEWrenchInteraction.use(player, wand, hit(charger, Direction.UP, 0.5), FocusAEWrenchInteraction.Action.LEFT_CLICK),
                "sneak left-click rotates a block that exposes an orientation strategy");
        var after = level.getBlockState(charger);
        helper.assertTrue(after != before, "the rotated block state differs");
        helper.assertTrue(IOrientationStrategy.get(before).getFacing(before) == Direction.NORTH
                && IOrientationStrategy.get(after).getFacing(after) == Direction.EAST,
                "the full strategy rotates clockwise around the clicked UP face");
        helper.assertTrue(WandApi.stored(wand, Aspects.FIRE) == 1000,
                "the rotate path also only simulates inside use()");
        helper.assertTrue(WandApi.consume(wand, player, FocusAEWrenchItem.castCost(), true) && WandApi.stored(wand, Aspects.AIR) == 990,
                "one original debit follows the successful rotate");
        // A plain stone block has no tile entity, so the original precheck chain refuses before rotating.
        var stone = charger.above();
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        var second = focusedWand(1000, 1000);
        helper.assertTrue(!FocusAEWrenchInteraction.use(player, second, hit(stone, Direction.UP, 0.5), FocusAEWrenchInteraction.Action.LEFT_CLICK),
                "a block without a tile entity is refused like the original preventAction chain");
        helper.assertTrue(WandApi.stored(second, Aspects.FIRE) == 1000 && WandApi.stored(second, Aspects.AIR) == 1000,
                "no vis for the refused left-click");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeFocusPedestalExclusion(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("thaumcraft:arcane_pedestal")).defaultBlockState());
        var player = fixturePlayer(helper);
        player.setShiftKeyDown(true);
        var wand = focusedWand(1000, 1000);
        helper.assertTrue(level.getBlockEntity(pos) instanceof IInfusionPedestal,
                "the core pedestal exposes the public infusion pedestal contract used for the exclusion");
        helper.assertTrue(!FocusAEWrenchInteraction.use(player, wand, hit(pos, Direction.UP, 0.5), FocusAEWrenchInteraction.Action.RIGHT_CLICK),
                "pedestals are excluded from the right-click block pop");
        helper.assertTrue(!FocusAEWrenchInteraction.use(player, wand, hit(pos, Direction.UP, 0.5), FocusAEWrenchInteraction.Action.LEFT_CLICK),
                "pedestals are excluded from the left-click rotate");
        helper.assertTrue(WandApi.stored(wand, Aspects.FIRE) == 1000 && WandApi.stored(wand, Aspects.AIR) == 1000,
                "no vis is touched for the excluded pedestal");
        helper.succeed();
    }

    private static JsonObject resource(String path) {
        try (var stream = ExtrasFocusNativeTest.class.getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing fixture resource " + path);
            }
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception failure) {
            throw new IllegalStateException("Cannot read fixture resource " + path, failure);
        }
    }

    @GameTest(template = "empty", timeoutTicks = 120)
    public static void nativeFacadeWhitelistAndCrucibleData(GameTestHelper helper) {
        var tag = resource("/data/ae2/tags/block/whitelisted/facades.json");
        var values = tag.getAsJsonArray("values");
        var expected = List.of("thaumcraft:amber_block", "thaumcraft:amber_bricks", "thaumcraft:obsidian_tile", "thaumcraft:tallow_block",
                "thaumcraft:thaumium_block", "thaumcraft:arcane_stone", "thaumcraft:arcane_stone_bricks", "thaumcraft:ancient_stone",
                "thaumcraft:greatwood_planks", "thaumcraft:silverwood_planks", "thaumcraft:fibrous_taint", "thaumcraft:tainted_flesh");
        helper.assertTrue(tag.get("replace").getAsBoolean() == false, "the facade whitelist merges instead of replacing AE2 values");
        var actual = new java.util.ArrayList<String>();
        for (var value : values) {
            actual.add(value.getAsString());
        }
        helper.assertTrue(actual.equals(expected), "the original whitelist maps to exactly the twelve existing port blocks");
        helper.assertTrue(!actual.contains("thaumcraft:warded_glass"), "warded glass stays out while the allowMirrors gate question is open");
        var certus = resource("/data/thaumicenergistics/recipe/certus_quartz_dupe.json");
        helper.assertTrue(certus.get("type").getAsString().equals("thaumcraft:crucible")
                && certus.get("research").getAsString().equals("thaumicenergistics:tecertusdupe")
                && certus.get("result").getAsJsonObject().get("count").getAsInt() == 2
                && certus.get("result").getAsJsonObject().get("id").getAsString().equals("ae2:certus_quartz_crystal")
                && certus.get("catalyst").getAsJsonObject().get("tag").getAsString().equals("thaumicenergistics:cell_certus_quartz")
                && certus.get("aspects").getAsJsonObject().get("vitreus").getAsInt() == 4
                && certus.get("aspects").getAsJsonObject().get("aqua").getAsInt() == 2,
                "certus duplication keeps the original crucible cost and doubles the plain crystal");
        var nether = resource("/data/thaumicenergistics/recipe/nether_quartz_dupe.json");
        helper.assertTrue(nether.get("result").getAsJsonObject().get("id").getAsString().equals("minecraft:quartz")
                && nether.get("catalyst").getAsJsonObject().get("tag").getAsString().equals("c:gems/quartz")
                && nether.get("aspects").getAsJsonObject().get("potentia").getAsInt() == 2,
                "nether quartz duplication adds the original potentia cost");
        var focusRecipe = resource("/data/thaumicenergistics/recipe/focus_ae_wrench.json");
        var pattern = focusRecipe.getAsJsonArray("pattern");
        helper.assertTrue(pattern.size() == 3 && pattern.get(0).getAsString().equals("ANF")
                && pattern.get(1).getAsString().equals("NWN") && pattern.get(2).getAsString().equals("FNA")
                && focusRecipe.get("vis").getAsJsonObject().get("aer").getAsInt() == 10
                && focusRecipe.get("vis").getAsJsonObject().get("ignis").getAsInt() == 10,
                "the focus recipe keeps the exact ANF/NWN/FNA pattern and whole-vis tens");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeExtrasResearchDefinitions(GameTestHelper helper) {
        var player = fixturePlayer(helper);
        var definitions = ResearchApi.definitions(player);
        var focus = definitions.get("thaumicenergistics:tefocuswrench");
        helper.assertTrue(focus != null && focus.secondary() && !focus.concealed() && focus.complexity() == 1
                && focus.column() == -3 && focus.row() == -7
                && focus.aspects().equals(Map.of("machina", 6, "instrumentum", 5, "praecantatio", 3))
                && focus.parents().equals(List.of("thaumicenergistics:pseudo_focusfire"))
                && focus.pageOrder().size() == 2,
                "original focus wrench research costs, coordinates, parent and two pages");
        var dupe = definitions.get("thaumicenergistics:tecertusdupe");
        helper.assertTrue(dupe != null && dupe.secondary() && dupe.concealed() && dupe.complexity() == 1
                && dupe.column() == -5 && dupe.row() == -5 && dupe.aspects().equals(Map.of("vitreus", 5))
                && dupe.parents().equals(List.of("thaumicenergistics:pseudo_alchemicalduplication"))
                && dupe.hiddenParents().equals(List.of("ALCHEMICALDUPLICATION"))
                && dupe.itemTriggers().equals(List.of("ae2:certus_quartz_crystal"))
                && dupe.pageOrder().size() == 3,
                "original certus dupe research with the real catalog item trigger and three pages");
        var focusFire = definitions.get("thaumicenergistics:pseudo_focusfire");
        helper.assertTrue(focusFire != null && focusFire.stub() && focusFire.hidden()
                && focusFire.linkedResearch().equals("FOCUSFIRE") && focusFire.column() == -4 && focusFire.row() == -7,
                "the FOCUSFIRE pseudo parent links the native research without bypassing it");
        var dupeParent = definitions.get("thaumicenergistics:pseudo_alchemicalduplication");
        helper.assertTrue(dupeParent != null && dupeParent.stub() && dupeParent.hidden()
                && dupeParent.linkedResearch().equals("ALCHEMICALDUPLICATION") && dupeParent.column() == -5 && dupeParent.row() == -6,
                "the ALCHEMICALDUPLICATION pseudo parent links the native research without bypassing it");
        helper.succeed();
    }

    private ExtrasFocusNativeTest() {}
}
