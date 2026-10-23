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
import thaumcraft.api.aspects.EssentiaItemApi;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.blocks.CellWorkbenchBlockEntity;
import thaumicenergistics.menu.CellWorkbenchMenu;
import thaumicenergistics.storage.CellContents;
import thaumicenergistics.storage.CellTier;
import thaumicenergistics.storage.EssentiaCell;
import thaumicenergistics.storage.EssentiaKey;

/**
 * Deferred verification suite for Part B (Tasks 13-24): Essentia Cell Workbench
 * server, container, and GUI lifecycle.
 *
 * NOTE: Execution is deferred by user instruction until all batches are reviewed.
 */
@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class CellWorkbenchBatch5Test {

    /**
     * Tasks 13, 14, 16, 21: Slot layout, cell attachment, ghost click interactions,
     * duplicate replacement, and live sync across multiple open menus.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void testLayoutAttachmentAndGhostClicks(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(pos, ThaumicEnergistics.CELL_WORKBENCH.get().defaultBlockState());
        var workbench = (CellWorkbenchBlockEntity) level.getBlockEntity(pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);

        var menu = new CellWorkbenchMenu(1, player.getInventory(), workbench);
        var viewer2 = new CellWorkbenchMenu(2, player.getInventory(), workbench);

        // Task 13: 63 ghosts, 1 cell at 63, 27 inventory, 9 hotbar = 100 slots total.
        helper.assertTrue(menu.slots.size() == 100, "Layout has exactly 100 slots (63 ghosts, 1 cell, 36 player)");
        helper.assertTrue(menu.getSlot(CellWorkbenchMenu.CELL_SLOT).getMaxStackSize() == 1, "Cell slot stack limit is 1");

        // Insert filled cell
        var stack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K4).get());
        var cell = new EssentiaCell(stack, null);
        cell.insert(EssentiaKey.of(Aspects.WATER), 50, Actionable.MODULATE, IActionSource.empty());
        cell.insert(EssentiaKey.of(Aspects.FIRE), 25, Actionable.MODULATE, IActionSource.empty());
        workbench.inventory().setItem(0, stack);
        menu.broadcastChanges();
        viewer2.broadcastChanges();

        // Task 14: Cell attached, hasCell() true
        helper.assertTrue(workbench.hasCell(), "Workbench reports hasCell == true");
        helper.assertTrue(workbench.cell() != null, "Workbench exposes active EssentiaCell handler");

        // Task 16: Click ghost slot with aspect item (Wisp Essence of Earth)
        var earthEssence = AspectApi.wispEssence(Aspects.EARTH);
        menu.setCarried(earthEssence.copy());
        menu.clicked(0, 0, ClickType.PICKUP, player);

        // Carried item must not be consumed or modified
        helper.assertTrue(ItemStack.matches(menu.getCarried(), earthEssence), "Ghost click does not consume carried item");
        helper.assertTrue(workbench.cell().ledger().contents().partitions().contains("terra"), "Aspect added to partition");

        // Task 21: Live sync to second viewer
        viewer2.broadcastChanges();
        helper.assertTrue(viewer2.filter(0).equals(EssentiaKey.of(Aspects.EARTH)), "Viewer 2 observes live partition sync");

        // Replacement can create duplicate entries (original 1.7.10 behavior)
        menu.setCarried(AspectApi.wispEssence(Aspects.EARTH));
        menu.clicked(0, 0, ClickType.PICKUP, player);
        menu.clicked(1, 0, ClickType.PICKUP, player);
        helper.assertTrue(workbench.cell().ledger().contents().partitions().equals(List.of("terra", "terra")),
                "Replacement retains duplicate entries per 1.7.10 parity");

        // Click with empty hand removes first matching aspect
        menu.setCarried(ItemStack.EMPTY);
        menu.clicked(0, 0, ClickType.PICKUP, player);
        helper.assertTrue(workbench.cell().ledger().contents().partitions().equals(List.of("terra")),
                "Empty hand click removes first matching partition entry");

        // Ghost slots cannot be manipulated by QUICK_MOVE, SWAP, CLONE, THROW
        menu.clicked(0, 0, ClickType.QUICK_MOVE, player);
        menu.clicked(0, 0, ClickType.SWAP, player);
        menu.clicked(0, 0, ClickType.CLONE, player);
        menu.clicked(0, 0, ClickType.THROW, player);
        helper.assertTrue(menu.getCarried().isEmpty(), "Ghost slots cannot be picked up or cloned");
        helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "Ghost slot cannot be swapped into hotbar");

        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    /**
     * Task 15: Aspect filter extraction from allowed items via IAspectItem and EssentiaItemApi.
     * Rejection of non-aspect items without private reflection.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void testAspectItemExtraction(GameTestHelper helper) {
        VerificationReports.install();

        // 1. Phial of essentia
        var waterPhial = EssentiaItemApi.fill(new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:empty_phial"))), Aspects.WATER, 8);
        if (waterPhial.isPresent()) {
            var key = CellWorkbenchMenu.aspectFromItem(waterPhial.get().resultStack());
            helper.assertTrue(key != null && key.equals(EssentiaKey.of(Aspects.WATER)), "Phial aspect correctly resolved via IAspectItem");
        }

        // 2. Empty phial (rejected)
        var emptyPhial = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:empty_phial")));
        helper.assertTrue(CellWorkbenchMenu.aspectFromItem(emptyPhial) == null, "Empty phial rejected as filter");

        // 3. Wisp essence
        var fireEssence = AspectApi.wispEssence(Aspects.FIRE);
        helper.assertTrue(CellWorkbenchMenu.aspectFromItem(fireEssence).equals(EssentiaKey.of(Aspects.FIRE)),
                "Wisp essence aspect correctly resolved");

        // 4. Configured jar label
        var labelItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:jar_label"));
        var configuredLabel = EssentiaItemApi.configureLabel(new ItemStack(labelItem), Aspects.AIR);
        if (configuredLabel.isPresent()) {
            var labelKey = CellWorkbenchMenu.aspectFromItem(configuredLabel.get());
            helper.assertTrue(labelKey != null && labelKey.equals(EssentiaKey.of(Aspects.AIR)),
                    "Configured label aspect resolved via EssentiaItemApi/IAspectItem");
        }

        // 5. Unrelated items (stone, diamond, empty stack)
        helper.assertTrue(CellWorkbenchMenu.aspectFromItem(new ItemStack(Items.DIAMOND)) == null, "Diamond rejected");
        helper.assertTrue(CellWorkbenchMenu.aspectFromItem(new ItemStack(Items.STICK)) == null, "Stick rejected");
        helper.assertTrue(CellWorkbenchMenu.aspectFromItem(ItemStack.EMPTY) == null, "Empty stack returns null");

        helper.succeed();
    }

    /**
     * Tasks 17, 18, 19: Partition to Contents (including creative cell quirk),
     * Clear button, and rejection of invalid/invented action modes.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void testPartitionToContentsAndClear(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(pos, ThaumicEnergistics.CELL_WORKBENCH.get().defaultBlockState());
        var workbench = (CellWorkbenchBlockEntity) level.getBlockEntity(pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);

        // 1. Ordinary cell: partition-to-contents copies aspects in exact stored order
        var stack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());
        var cell = new EssentiaCell(stack, null);
        cell.insert(EssentiaKey.of(Aspects.WATER), 10, Actionable.MODULATE, IActionSource.empty());
        cell.insert(EssentiaKey.of(Aspects.EARTH), 20, Actionable.MODULATE, IActionSource.empty());
        cell.insert(EssentiaKey.of(Aspects.ORDER), 5, Actionable.MODULATE, IActionSource.empty());
        workbench.inventory().setItem(0, stack);

        var menu = new CellWorkbenchMenu(1, player.getInventory(), workbench);

        // Action 0: PARTITION_CONTENTS
        helper.assertTrue(menu.clickMenuButton(player, CellWorkbenchMenu.PARTITION_CONTENTS), "Partition-to-contents accepted");
        helper.assertTrue(workbench.cell().ledger().contents().partitions().equals(List.of("aqua", "terra", "ordo")),
                "Partitions match exact order of stored contents");

        // Action 1: CLEAR (Task 18)
        helper.assertTrue(menu.clickMenuButton(player, CellWorkbenchMenu.CLEAR), "Clear accepted");
        helper.assertTrue(workbench.cell().ledger().contents().partitions().isEmpty(), "Partitions emptied by Clear");
        helper.assertTrue(workbench.cell().ledger().total() == 35, "Real stored cell contents completely preserved after Clear");

        // Task 19: Invented/unsupported button modes rejected
        helper.assertTrue(!menu.clickMenuButton(player, 2), "Button ID 2 rejected");
        helper.assertTrue(!menu.clickMenuButton(player, 3), "Button ID 3 rejected");
        helper.assertTrue(!menu.clickMenuButton(player, -1), "Button ID -1 rejected");
        helper.assertTrue(!menu.clickMenuButton(player, 999), "Button ID 999 rejected");

        // 2. Creative cell quirk (Task 17): partition-to-contents CLEARS configuration
        var creativeStack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.CREATIVE).get());
        var creativeCell = new EssentiaCell(creativeStack, null);
        creativeCell.partitions(List.of("ignis", "aer"));
        workbench.inventory().setItem(0, creativeStack);
        menu.broadcastChanges();

        helper.assertTrue(menu.clickMenuButton(player, CellWorkbenchMenu.PARTITION_CONTENTS),
                "Creative cell partition-to-contents executed");
        helper.assertTrue(workbench.cell().ledger().contents().partitions().isEmpty(),
                "Creative cell partition-to-contents clears configuration per 1.7.10 quirk");

        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    /**
     * Tasks 20, 22: Server validation (player identity, BE identity, distance, chunk)
     * and shift-transfer of real slots in both directions.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void testServerValidationAndShiftTransfer(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        level.setBlockAndUpdate(pos, ThaumicEnergistics.CELL_WORKBENCH.get().defaultBlockState());
        var workbench = (CellWorkbenchBlockEntity) level.getBlockEntity(pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);

        var menu = new CellWorkbenchMenu(1, player.getInventory(), workbench);

        // Task 20: Identity and distance checks
        helper.assertTrue(menu.stillValid(player), "Menu is valid for opening player");
        var impostor = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.assertTrue(!menu.stillValid(impostor), "Menu is invalid for other player");

        // Distance check: move far away
        player.setPos(pos.getX() + 50.0, pos.getY(), pos.getZ());
        helper.assertTrue(!menu.stillValid(player), "Menu invalid when player is too far (> 8 blocks)");
        player.setPos(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
        helper.assertTrue(menu.stillValid(player), "Menu re-validates when player is back in range");

        // Task 22: Shift-transfer real cell from player inventory to workbench
        var cellStack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K16).get());
        player.getInventory().setItem(9, cellStack);
        menu.quickMoveStack(player, 64);
        helper.assertTrue(player.getInventory().getItem(9).isEmpty(), "Cell shift-moved out of player inventory");
        helper.assertTrue(workbench.hasCell() && workbench.inventory().getItem(0).getItem() == cellStack.getItem(),
                "Cell arrived in workbench slot 63");

        // Shift-transfer real cell back to player inventory
        menu.quickMoveStack(player, CellWorkbenchMenu.CELL_SLOT);
        helper.assertTrue(!workbench.hasCell(), "Cell removed from workbench via shift-click");
        helper.assertTrue(!player.getInventory().getItem(9).isEmpty(), "Cell returned to player inventory");

        // Shift-clicking aspect item adds to partition without consuming or transferring
        workbench.inventory().setItem(0, cellStack);
        var fireWisp = AspectApi.wispEssence(Aspects.FIRE);
        player.getInventory().setItem(9, fireWisp.copy());
        menu.quickMoveStack(player, 64);
        helper.assertTrue(ItemStack.matches(player.getInventory().getItem(9), fireWisp),
                "Aspect item in player inventory NOT consumed on shift-partition");
        helper.assertTrue(workbench.cell().ledger().contents().partitions().contains("ignis"),
                "Aspect added to partition list via shift-click");

        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        helper.succeed();
    }

    /**
     * Tasks 23, 24: Unload/save/reload persistence, no runtime grid serialization,
     * and break/wrench single-drop guarantees.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void testPersistenceAndDropPaths(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3, 2, 3));
        var state = ThaumicEnergistics.CELL_WORKBENCH.get().defaultBlockState();
        level.setBlockAndUpdate(pos, state);
        var workbench = (CellWorkbenchBlockEntity) level.getBlockEntity(pos);

        // Configure cell with unknown addon aspect tag and duplicate
        var stack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K64).get());
        var cell = new EssentiaCell(stack, null);
        cell.insert(EssentiaKey.of(Aspects.ORDER), 100, Actionable.MODULATE, IActionSource.empty());
        cell.partitions(List.of("ordo", "ordo", "addon:unknown_aspect"));
        workbench.inventory().setItem(0, stack);

        // Task 23: Save and restore NBT
        var savedTag = workbench.saveWithFullMetadata(level.registryAccess());
        helper.assertTrue(savedTag.contains("EssentiaCell"), "NBT contains EssentiaCell tag");
        helper.assertTrue(!savedTag.contains("grid"), "NBT does not serialize runtime grid");

        level.removeBlockEntity(pos);
        var restored = new CellWorkbenchBlockEntity(pos, state);
        restored.loadWithComponents(savedTag, level.registryAccess());
        level.setBlockEntity(restored);

        helper.assertTrue(restored.hasCell(), "Restored BE has cell");
        helper.assertTrue(restored.cell().ledger().contents().partitions().equals(List.of("ordo", "ordo", "addon:unknown_aspect")),
                "Restored cell retains duplicate tags and unknown addon aspects");
        helper.assertTrue(restored.cell().ledger().total() == 100, "Restored cell retains stored amount");

        // Task 24: Break drops cell once and block once
        level.destroyBlock(pos, true);
        var drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        long cellDrops = drops.stream().filter(e -> e.getItem().getItem() == stack.getItem()).count();
        long workbenchDrops = drops.stream().filter(e -> e.getItem().is(ThaumicEnergistics.CELL_WORKBENCH_ITEM.get())).count();

        helper.assertTrue(cellDrops == 1, "Normal break drops the cell exactly once");
        helper.assertTrue(workbenchDrops == 1, "Normal break drops the workbench block exactly once");
        drops.forEach(ItemEntity::discard);

        // Sneak-wrench dismantle
        level.setBlockAndUpdate(pos, state);
        var wrenchedBE = (CellWorkbenchBlockEntity) level.getBlockEntity(pos);
        wrenchedBE.inventory().setItem(0, stack.copy());

        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        var wrench = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:nether_quartz_wrench")).getDefaultInstance();
        player.setShiftKeyDown(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, wrench);

        var result = state.useItemOn(wrench, level, player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false));
        helper.assertTrue(result.consumesAction() && level.isEmptyBlock(pos), "AE2 wrench dismantles block while sneaking");

        var wrenchedDrops = level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2));
        long wrenchedCellDrops = wrenchedDrops.stream().filter(e -> e.getItem().getItem() == stack.getItem()).count();
        long wrenchedBlockDrops = wrenchedDrops.stream().filter(e -> e.getItem().is(ThaumicEnergistics.CELL_WORKBENCH_ITEM.get())).count();

        helper.assertTrue(wrenchedCellDrops == 1, "Wrench dismantle drops cell exactly once");
        helper.assertTrue(wrenchedBlockDrops == 1, "Wrench dismantle drops workbench block exactly once");
        wrenchedDrops.forEach(ItemEntity::discard);

        helper.succeed();
    }
}
