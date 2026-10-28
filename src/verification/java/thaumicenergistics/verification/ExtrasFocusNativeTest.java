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
import net.minecraft.world.InteractionHand;
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
import thaumicenergistics.extras.FocusAEWrenchBehavior;
import thaumicenergistics.extras.FocusAEWrenchInteraction;
import thaumicenergistics.extras.FocusAEWrenchItem;

/**
 * Unrun verification fixtures for the batch-2 extras: the AE wrench focus
 * interaction path (refusals, exact-hit part dismantle, held/shift restore,
 * orientable rotate, pedestal exclusion, infusion matrix protection B2-MATRIX,
 * shared cooldown, exact vis accounting), the facade whitelist data, the
 * crucible recipe data with neoforge conditions, and the four research definitions.
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
        PartHelper.setPart(level, cable, Direction.UP, null, ThaumicEnergistics.IMPORT_BUS.get());
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
        var cable = helper.absolutePos(new BlockPos(3, 2, 3));
        setCableWithImportBus(helper, cable);
        var player = fixturePlayer(helper);
        var charged = focusedWand(1000, 1000);
        player.setItemInHand(InteractionHand.MAIN_HAND, charged);
        var hit = hit(cable, Direction.UP, 0.875);
        // Original precheck order starts with sneaking; nothing else may run without it.
        helper.assertTrue(!FocusAEWrenchInteraction.use(player, charged, hit, FocusAEWrenchInteraction.Action.RIGHT_CLICK),
                "focus refuses a right-click while the player is not sneaking");
        helper.assertTrue(WandApi.stored(charged, Aspects.FIRE) == 1000 && WandApi.stored(charged, Aspects.AIR) == 1000,
                "no vis is simulated away or debited for a refused action");

        // Player state validation: fake player / distance / not sneaking.
        player.setShiftKeyDown(true);
        var farHit = hit(cable.offset(10, 0, 10), Direction.UP, 0.875);
        helper.assertTrue(!FocusAEWrenchInteraction.use(player, charged, farHit, FocusAEWrenchInteraction.Action.RIGHT_CLICK),
                "focus refuses interactions beyond reach distance");

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
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
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
                "use() itself does not do redundant debit; right-click debit belongs to the core casting pipeline");
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
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        var before = level.getBlockState(charger);
        helper.assertTrue(FocusAEWrenchInteraction.use(player, wand, hit(charger, Direction.UP, 0.5), FocusAEWrenchInteraction.Action.LEFT_CLICK),
                "sneak left-click rotates a block that exposes an orientation strategy");
        var after = level.getBlockState(charger);
        helper.assertTrue(after != before, "the rotated block state differs");
        helper.assertTrue(IOrientationStrategy.get(before).getFacing(before) == Direction.NORTH
                && IOrientationStrategy.get(after).getFacing(after) == Direction.EAST,
                "the full strategy rotates clockwise around the clicked UP face");

        // A plain stone block has no tile entity, so the original precheck chain refuses before rotating.
        var stone = charger.above();
        level.setBlockAndUpdate(stone, Blocks.STONE.defaultBlockState());
        var second = focusedWand(1000, 1000);
        player.setItemInHand(InteractionHand.MAIN_HAND, second);
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
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
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

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeFocusMatrixProtection(GameTestHelper helper) {
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        var matrixBlock = BuiltInRegistries.BLOCK.get(ResourceLocation.parse("thaumcraft:infusion_matrix"));
        level.setBlockAndUpdate(pos, matrixBlock.defaultBlockState());
        var player = fixturePlayer(helper);
        player.setShiftKeyDown(true);
        var wand = focusedWand(1000, 1000);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);

        helper.assertTrue(FocusAEWrenchInteraction.isMatrix(level.getBlockState(pos)),
                "matrix block is recognized by the public isMatrix checker");

        // Right-click MUST be refused and MUST NOT pop/dismantle the matrix!
        boolean rightResult = FocusAEWrenchInteraction.use(player, wand, hit(pos, Direction.UP, 0.5), FocusAEWrenchInteraction.Action.RIGHT_CLICK);
        helper.assertTrue(!rightResult, "infusion matrix right-click is refused from blockPop");
        helper.assertTrue(level.getBlockState(pos).is(matrixBlock), "infusion matrix block remains intact after right-click");

        // Left-click without probe installed is a safe no-op.
        boolean leftResult = FocusAEWrenchInteraction.use(player, wand, hit(pos, Direction.UP, 0.5), FocusAEWrenchInteraction.Action.LEFT_CLICK);
        helper.assertTrue(!leftResult, "infusion matrix left-click is safe no-op while B2-MATRIX probe is pending");
        helper.assertTrue(level.getBlockState(pos).is(matrixBlock), "infusion matrix block remains intact after left-click");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void nativeFocusSharedCooldown(GameTestHelper helper) {
        var player = fixturePlayer(helper);
        var wand = focusedWand(1000, 1000);
        player.setItemInHand(InteractionHand.MAIN_HAND, wand);
        helper.assertTrue(thaumcraft.api.wands.FocusSwingApi.tryBegin(player, wand), "first swing claims the native cooldown");
        helper.assertTrue(!thaumcraft.api.wands.FocusSwingApi.tryBegin(player, wand), "a second swing cannot reuse it");
        helper.assertTrue(WandApi.stored(wand, Aspects.FIRE) == 1000 && WandApi.stored(wand, Aspects.AIR) == 1000,
                "claiming a cooldown does not debit Vis");
        var anotherWand = focusedWand(1000, 1000);
        WandApi.focus(anotherWand, new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:focus_frost"))));
        WandApi.set(anotherWand, Aspects.WATER, 1000);
        player.setItemInHand(InteractionHand.MAIN_HAND, anotherWand);
        player.gameMode.useItem(player, player.serverLevel(), anotherWand, InteractionHand.MAIN_HAND);
        helper.assertTrue(WandApi.stored(anotherWand, Aspects.WATER) == 1000,
                "the native Frost right-click shares the cooldown claimed by the addon swing");
        helper.assertTrue(!thaumcraft.api.wands.FocusSwingApi.tryBegin(player, anotherWand),
                "changing wands and selected focus cannot bypass the player's shared cooldown");
        helper.runAfterDelay(19, () -> helper.assertTrue(
                !thaumcraft.api.wands.FocusSwingApi.tryBegin(player, anotherWand), "1000ms still blocks tick 19"));
        helper.runAfterDelay(20, () -> {
            helper.assertTrue(thaumcraft.api.wands.FocusSwingApi.tryBegin(player, anotherWand), "1000ms expires at tick 20");
            helper.succeed();
        });
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
        JsonObject tag;
        try {
            var packs = helper.getLevel().getServer().getResourceManager().getResourceStack(
                    ResourceLocation.fromNamespaceAndPath("ae2", "tags/block/whitelisted/facades.json"));
            var generated = packs.stream().filter(value -> value.sourcePackId().equals("thaumicenergistics/tc_facades"))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Missing active facade pack"));
            try (var reader = generated.openAsReader()) {
                tag = JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("Cannot read active facade pack", failure);
        }
        var values = tag.getAsJsonArray("values");
        var expected = List.of("thaumcraft:amber_block", "thaumcraft:amber_bricks", "thaumcraft:warded_glass",
                "thaumcraft:obsidian_tile", "thaumcraft:thaumium_block", "thaumcraft:tallow_block",
                "thaumcraft:arcane_stone", "thaumcraft:arcane_stone_bricks", "thaumcraft:ancient_stone",
                "thaumcraft:greatwood_planks", "thaumcraft:silverwood_planks", "thaumcraft:fibrous_taint",
                "thaumcraft:tainted_flesh").stream()
                .filter(ignored -> thaumicenergistics.extras.ExtrasConfig.forceTCFacades())
                .filter(id -> !id.equals("thaumcraft:warded_glass") || thaumcraft.api.world.ContentRules.mirrorsEnabled())
                .toList();
        helper.assertTrue(tag.get("replace").getAsBoolean() == false, "the facade whitelist merges instead of replacing AE2 values");
        var actual = new java.util.ArrayList<String>();
        for (var value : values) {
            var entry = value.getAsJsonObject();
            actual.add(entry.get("id").getAsString());
            helper.assertTrue(!entry.get("required").getAsBoolean(), "absent legacy counterparts stay optional");
        }
        helper.assertTrue(actual.equals(expected), "the active facade pack respects both original option gates");
        helper.assertTrue(actual.contains("thaumcraft:warded_glass"), "warded glass is whitelisted with active allowMirrors");

        var certus = resource("/data/thaumicenergistics/recipe/certus_quartz_dupe.json");
        helper.assertTrue(certus.get("type").getAsString().equals("thaumcraft:crucible")
                && certus.get("research").getAsString().equals("thaumicenergistics:tecertusdupe")
                && certus.get("result").getAsJsonObject().get("count").getAsInt() == 2
                && certus.get("result").getAsJsonObject().get("id").getAsString().equals("ae2:certus_quartz_crystal")
                && certus.get("catalyst").getAsJsonObject().get("tag").getAsString().equals("thaumicenergistics:cell_certus_quartz")
                && certus.get("aspects").getAsJsonObject().get("vitreus").getAsInt() == 4
                && certus.get("aspects").getAsJsonObject().get("aqua").getAsInt() == 2,
                "certus duplication keeps the original crucible cost and doubles the plain crystal");
        helper.assertTrue(certus.getAsJsonArray("neoforge:conditions").size() == 3,
                "certus dupe has extras feature, missing-tag and item-exists conditions");

        var nether = resource("/data/thaumicenergistics/recipe/nether_quartz_dupe.json");
        helper.assertTrue(nether.get("result").getAsJsonObject().get("id").getAsString().equals("minecraft:quartz")
                && nether.get("catalyst").getAsJsonObject().get("tag").getAsString().equals("c:gems/quartz")
                && nether.get("aspects").getAsJsonObject().get("potentia").getAsInt() == 2,
                "nether quartz duplication adds the original potentia cost");
        helper.assertTrue(nether.getAsJsonArray("neoforge:conditions").size() == 3,
                "nether dupe has extras feature, missing-tag and item-exists conditions");

        var focusRecipe = resource("/data/thaumicenergistics/recipe/focus_ae_wrench.json");
        var pattern = focusRecipe.getAsJsonArray("pattern");
        helper.assertTrue(pattern.size() == 3 && pattern.get(0).getAsString().equals("ANF")
                && pattern.get(1).getAsString().equals("NWN") && pattern.get(2).getAsString().equals("FNA")
                && focusRecipe.get("vis").getAsJsonObject().get("aer").getAsInt() == 10
                && focusRecipe.get("vis").getAsJsonObject().get("ignis").getAsInt() == 10,
                "the focus recipe keeps the exact ANF/NWN/FNA pattern and whole-vis tens");
        helper.assertTrue(focusRecipe.getAsJsonArray("neoforge:conditions").size() == 2,
                "focus recipe has extras feature and item-exists conditions");
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
