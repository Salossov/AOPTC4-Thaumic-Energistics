package thaumicenergistics.verification;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectApi;
import thaumcraft.api.aspects.Aspects;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.blocks.CellWorkbenchBlockEntity;
import thaumicenergistics.menu.CellWorkbenchMenu;
import thaumicenergistics.storage.CellTier;
import thaumicenergistics.storage.EssentiaCell;
import thaumicenergistics.storage.EssentiaKey;

@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class WorkbenchNativeTest {
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeWorkbenchPartitionRules(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3,2,3));
        level.setBlockAndUpdate(pos, ThaumicEnergistics.CELL_WORKBENCH.get().defaultBlockState());
        var workbench = (CellWorkbenchBlockEntity) level.getBlockEntity(pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + .5, pos.getY() + 1, pos.getZ() + .5);
        var menu = new CellWorkbenchMenu(1, player.getInventory(), workbench);
        var anotherViewer = new CellWorkbenchMenu(2, player.getInventory(), workbench);
        helper.assertTrue(menu.stillValid(player) && menu.slots.size() == 100, "native workbench menu has 63 ghosts, one cell and 36 player slots");
        var stack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());
        var cell = new EssentiaCell(stack, null);
        cell.insert(EssentiaKey.of(Aspects.WATER),40,Actionable.MODULATE,IActionSource.empty());
        cell.insert(EssentiaKey.of(Aspects.EARTH),17,Actionable.MODULATE,IActionSource.empty());
        player.getInventory().setItem(9,stack);
        menu.quickMoveStack(player,64);
        helper.assertTrue(player.getInventory().getItem(9).isEmpty() && workbench.cell().ledger().total() == 57, "shift moves actual filled cell into workbench without losing contents");
        helper.assertTrue(menu.clickMenuButton(player,CellWorkbenchMenu.PARTITION_CONTENTS), "partition-current accepted");
        helper.assertTrue(workbench.cell().ledger().contents().partitions().equals(List.of("aqua","terra")), "partition-current follows original occupied storage order");
        anotherViewer.broadcastChanges();
        helper.assertTrue(anotherViewer.filter(1).equals(EssentiaKey.of(Aspects.EARTH)), "other open menu observes partition edits");
        var fire = AspectApi.wispEssence(Aspects.FIRE);
        menu.setCarried(fire.copy());
        menu.clicked(50,0,ClickType.PICKUP,player);
        helper.assertTrue(workbench.cell().ledger().contents().partitions().equals(List.of("aqua","terra","ignis")) && ItemStack.matches(menu.getCarried(),fire), "clicking empty grid appends without consuming cursor");
        menu.setCarried(AspectApi.wispEssence(Aspects.WATER));
        menu.clicked(1,0,ClickType.PICKUP,player);
        helper.assertTrue(workbench.cell().ledger().contents().partitions().equals(List.of("aqua","aqua","ignis")), "original replacement keeps duplicate partition entries");
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(1,0,ClickType.PICKUP,player);
        helper.assertTrue(workbench.cell().ledger().contents().partitions().equals(List.of("aqua","ignis")), "duplicate removal acts on first matching aspect as original packet");
        menu.clickMenuButton(player,CellWorkbenchMenu.CLEAR);
        var aspects = Aspect.all().values().stream().sorted(java.util.Comparator.comparing(Aspect::getTag)).limit(13).toList();
        for (var aspect : aspects) {
            menu.setCarried(AspectApi.wispEssence(aspect));
            menu.clicked(62,0,ClickType.PICKUP,player);
        }
        helper.assertTrue(workbench.cell().ledger().contents().partitions().size() == 13 && workbench.cell().ledger().usedTypes() == 2,
                "ordinary cell whitelist exceeds twelve while stored contents still use two types");
        helper.assertTrue(workbench.cell().ledger().total() == 57, "all partition mutations leave existing essentia intact");
        menu.clicked(0,0,ClickType.QUICK_MOVE,player);
        menu.quickMoveStack(player,0);
        helper.assertTrue(workbench.cell().ledger().contents().partitions().size() == 13, "ghost slots cannot be shift-extracted");
        helper.assertTrue(!menu.clickMenuButton(player,999), "unknown workbench command refused");
        menu.quickMoveStack(player,CellWorkbenchMenu.CELL_SLOT);
        helper.assertTrue(workbench.cell() == null && !menu.clickMenuButton(player,CellWorkbenchMenu.CLEAR), "physical cell can be removed and empty workbench rejects edits");
        helper.assertTrue(player.getInventory().getItem(9).getOrDefault(ThaumicEnergistics.CELL_CONTENTS,
                thaumicenergistics.storage.CellContents.EMPTY).partitions().size() == 13, "removed cell carries its real partition data");
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        helper.assertTrue(!menu.stillValid(player), "removed workbench invalidates menu");
        System.out.println("THE_NATIVE_WORKBENCH_PARTITIONS_PASS");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeWorkbenchPersistenceAndDrops(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3,2,3));
        var state = ThaumicEnergistics.CELL_WORKBENCH.get().defaultBlockState();
        var recipe = level.getRecipeManager().byKey(ThaumicEnergistics.id("essentia_cell_workbench")).orElseThrow().value();
        var input = net.minecraft.world.item.crafting.CraftingInput.of(2,1,List.of(
                BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:cell_workbench")).getDefaultInstance(),
                ThaumicEnergistics.COMPONENT_ITEMS.get(CellTier.K1).get().getDefaultInstance()));
        helper.assertTrue(recipe instanceof net.minecraft.world.item.crafting.CraftingRecipe crafting
                && crafting.matches(input,level) && crafting.assemble(input,level.registryAccess()).is(ThaumicEnergistics.CELL_WORKBENCH_ITEM.get()),
                "native shapeless workbench recipe uses original 1K component and modern AE2 cell workbench");
        level.setBlockAndUpdate(pos,state);
        var workbench = (CellWorkbenchBlockEntity) level.getBlockEntity(pos);
        var handler = level.getCapability(Capabilities.ItemHandler.BLOCK,pos,Direction.DOWN);
        helper.assertTrue(handler != null && handler.getSlotLimit(0) == 1 && handler.insertItem(0,new ItemStack(Items.DIAMOND),false).is(Items.DIAMOND), "native inventory capability accepts only one essentia cell");
        var stack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K4).get());
        var cell = new EssentiaCell(stack,null);
        cell.insert(EssentiaKey.of(Aspects.FIRE), 123, Actionable.MODULATE, IActionSource.empty());
        cell.partitions(List.of("ignis","aqua","aqua","missing:addon"));
        helper.assertTrue(handler.insertItem(0,stack,false).isEmpty(), "automation inserts real cell");
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        var menu = new CellWorkbenchMenu(1,player.getInventory(),workbench);
        var expected = workbench.inventory().getItem(0).copy();
        var saved = workbench.saveWithFullMetadata(level.registryAccess());
        level.removeBlockEntity(pos);
        var restored = new CellWorkbenchBlockEntity(pos,state);
        restored.loadWithComponents(saved,level.registryAccess());
        level.setBlockEntity(restored);
        helper.assertTrue(ItemStack.matches(expected,restored.inventory().getItem(0)), "native block-entity NBT retains filled cell, unknown tags and duplicates");
        helper.assertTrue(!menu.stillValid(player), "new block-entity instance cannot be edited through stale menu");
        level.destroyBlock(pos,true);
        var drops = level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2));
        helper.assertTrue(drops.stream().filter(e -> ItemStack.matches(expected,e.getItem())).count() == 1, "normal break drops the filled cell exactly once");
        helper.assertTrue(drops.stream().filter(e -> e.getItem().is(ThaumicEnergistics.CELL_WORKBENCH_ITEM.get())).mapToInt(e -> e.getItem().getCount()).sum() == 1, "normal break loot drops one workbench");
        drops.forEach(ItemEntity::discard);
        level.setBlockAndUpdate(pos,state);
        var next = (CellWorkbenchBlockEntity) level.getBlockEntity(pos);
        next.inventory().setItem(0,expected.copy());
        var wrench = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:nether_quartz_wrench")).getDefaultInstance();
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND,wrench);
        var result = state.useItemOn(wrench,level,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.UP,pos,false));
        helper.assertTrue(result.consumesAction() && level.isEmptyBlock(pos), "native AE2 wrench dismantles workbench while sneaking");
        var wrenched = level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(2));
        helper.assertTrue(wrenched.stream().filter(e -> ItemStack.matches(expected,e.getItem())).count() == 1
                && wrenched.stream().filter(e -> e.getItem().is(ThaumicEnergistics.CELL_WORKBENCH_ITEM.get())).mapToInt(e -> e.getItem().getCount()).sum() == 1,
                "wrench drops one unchanged cell and one workbench");
        wrenched.forEach(ItemEntity::discard);
        System.out.println("THE_NATIVE_WORKBENCH_PERSISTENCE_PASS");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeCreativeWorkbenchQuirks(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3,2,3));
        level.setBlockAndUpdate(pos,ThaumicEnergistics.CELL_WORKBENCH.get().defaultBlockState());
        var workbench = (CellWorkbenchBlockEntity) level.getBlockEntity(pos);
        var stack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.CREATIVE).get());
        var cell = new EssentiaCell(stack,null);
        cell.partitions(List.of("aqua","aqua"));
        var counter = new KeyCounter(); cell.getAvailableStacks(counter);
        helper.assertTrue(counter.get(EssentiaKey.of(Aspects.WATER)) == 4_000_000_000L, "original duplicate creative partitions add their displayed amounts");
        workbench.inventory().setItem(0,stack);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        var menu = new CellWorkbenchMenu(1,player.getInventory(),workbench);
        helper.assertTrue(menu.clickMenuButton(player,CellWorkbenchMenu.PARTITION_CONTENTS) && workbench.cell().ledger().contents().partitions().isEmpty(),
                "original workbench partitions creative cell from empty real contents, clearing its virtual sources");
        cell.partitions(java.util.stream.IntStream.range(0,63).mapToObj(i -> "test:a"+i).toList());
        menu.broadcastChanges();
        menu.setCarried(AspectApi.wispEssence(Aspects.FIRE));
        menu.clicked(62,0,ClickType.PICKUP,player);
        helper.assertTrue(workbench.cell().ledger().contents().partitions().size() == 63 && workbench.cell().ledger().contents().partitions().get(62).equals("ignis"),
                "all 63 original partition widgets remain addressable");
        workbench.inventory().removeItemNoUpdate(0);
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        System.out.println("THE_NATIVE_CREATIVE_WORKBENCH_PASS");
        helper.succeed();
    }
}
