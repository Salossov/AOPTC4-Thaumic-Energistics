package thaumicenergistics.verification;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.ChatFormatting;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.Aspects;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.storage.CellContents;
import thaumicenergistics.storage.CellLedger;
import thaumicenergistics.storage.CellTier;
import thaumicenergistics.storage.EssentiaCell;
import thaumicenergistics.storage.EssentiaCellItem;
import thaumicenergistics.storage.EssentiaKey;

/**
 * Deferred verification suite for Batch 5 Part A: Essentia Storage Cells (Tasks 1-12).
 * Verifies strict parity with 1.1.3.0 release and 1.7.10 ThE implementation.
 *
 * DO NOT EXECUTE AUTOMATICALLY. Verification deferred by user request.
 */
@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class StorageCellsBatch5Test {
    private static final IActionSource SOURCE = IActionSource.empty();

    /**
     * Tasks 1, 2, 3: Tier order, capacities, 2 essentia/byte ratio, types limits, idle drain, and component binding.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void test1_2_3_tiersCapacitiesAndDrains(GameTestHelper helper) {
        VerificationReports.install();

        // Task 1: Tier ordering, indices, and rarities
        CellTier[] tiers = CellTier.values();
        helper.assertTrue(tiers.length == 5, "Exact 5 cell tiers defined");
        helper.assertTrue(tiers[0] == CellTier.K1 && tiers[0].index == 0 && tiers[0].rarity == Rarity.COMMON, "Tier 0 is 1k COMMON");
        helper.assertTrue(tiers[1] == CellTier.K4 && tiers[1].index == 1 && tiers[1].rarity == Rarity.UNCOMMON, "Tier 1 is 4k UNCOMMON");
        helper.assertTrue(tiers[2] == CellTier.K16 && tiers[2].index == 2 && tiers[2].rarity == Rarity.UNCOMMON, "Tier 2 is 16k UNCOMMON");
        helper.assertTrue(tiers[3] == CellTier.K64 && tiers[3].index == 3 && tiers[3].rarity == Rarity.RARE, "Tier 3 is 64k RARE");
        helper.assertTrue(tiers[4] == CellTier.CREATIVE && tiers[4].index == 4 && tiers[4].rarity == Rarity.EPIC, "Tier 4 is creative EPIC");

        // Component existence: only normal cells have components
        for (CellTier tier : tiers) {
            helper.assertTrue(tier.hasComponent() == !tier.creative(), "Component only exists for non-creative cells");
            helper.assertTrue(CellTier.byIndex(tier.index) == tier, "Index lookup matches ordinal");
            helper.assertTrue(CellTier.bySuffix(tier.suffix) == tier, "Suffix lookup matches tier");
        }

        // Task 2: Exact byte capacities, 2 essentia/byte ratio, 12 types, 16348 quirk preserved
        helper.assertTrue(CellTier.K1.bytes == 1024 && CellTier.K1.capacity() == 2048, "1k capacity = 1024 bytes (2048 essentia)");
        helper.assertTrue(CellTier.K4.bytes == 4096 && CellTier.K4.capacity() == 8192, "4k capacity = 4096 bytes (8192 essentia)");
        // 16348 is NOT a typo! Preserves upstream 1.7.10 ThE quirk
        helper.assertTrue(CellTier.K16.bytes == 16348 && CellTier.K16.capacity() == 32696, "16k capacity = exactly 16348 bytes (32696 essentia)");
        helper.assertTrue(CellTier.K64.bytes == 65536 && CellTier.K64.capacity() == 131072, "64k capacity = 65536 bytes (131072 essentia)");
        helper.assertTrue(CellTier.CREATIVE.bytes == 0 && CellTier.CREATIVE.capacity() == 0, "Creative cell has 0 byte capacity");

        for (CellTier tier : tiers) {
            if (!tier.creative()) {
                helper.assertTrue(tier.types == 12, "Normal cell has 12 types limit");
            } else {
                helper.assertTrue(tier.types == 63, "Creative cell has 63 types limit");
            }
        }

        // Task 3: Idle drains (0.5, 1.0, 1.5, 2.0, 0.0)
        helper.assertTrue(CellTier.K1.idleDrain == 0.5, "1k idle drain = 0.5 AE/t");
        helper.assertTrue(CellTier.K4.idleDrain == 1.0, "4k idle drain = 1.0 AE/t");
        helper.assertTrue(CellTier.K16.idleDrain == 1.5, "16k idle drain = 1.5 AE/t");
        helper.assertTrue(CellTier.K64.idleDrain == 2.0, "64k idle drain = 2.0 AE/t");
        helper.assertTrue(CellTier.CREATIVE.idleDrain == 0.0, "Creative idle drain = 0.0 AE/t");

        // Verify StorageCells binding on item instances
        for (CellTier tier : tiers) {
            ItemStack stack = new ItemStack(ThaumicEnergistics.CELLS.get(tier).get());
            EssentiaCell cell = (EssentiaCell) StorageCells.getCellInventory(stack, () -> {});
            helper.assertTrue(cell != null, "AE2 recognizes cell handler for tier " + tier.suffix);
            helper.assertTrue(cell.getIdleDrain() == tier.idleDrain, "getIdleDrain matches tier idleDrain");
        }

        helper.succeed();
    }

    /**
     * Tasks 4, 5: Insertion and extraction semantics, zero/negative cutoffs, type slot limit (12),
     * accepted count return value, hole reuse preservation, and SIMULATE safety.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void test4_5_insertionExtractionAndHoleReuse(GameTestHelper helper) {
        VerificationReports.install();

        ItemStack stack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K64).get());
        AtomicInteger saves = new AtomicInteger();
        EssentiaCell cell = new EssentiaCell(stack, saves::incrementAndGet);

        EssentiaKey aer = EssentiaKey.of(Aspects.AIR);
        EssentiaKey aqua = EssentiaKey.of(Aspects.WATER);
        EssentiaKey ignis = EssentiaKey.of(Aspects.FIRE);
        EssentiaKey terra = EssentiaKey.of(Aspects.EARTH);

        // Task 4: Cutoff <= 0
        helper.assertTrue(cell.insert(aer, 0, Actionable.MODULATE, SOURCE) == 0, "Insert 0 returns 0 accepted");
        helper.assertTrue(cell.insert(aer, -5, Actionable.MODULATE, SOURCE) == 0, "Insert negative returns 0 accepted");

        // Accepted count returned (not remainder!)
        long acceptedAer = cell.insert(aer, 100, Actionable.MODULATE, SOURCE);
        helper.assertTrue(acceptedAer == 100, "Insert returns accepted count 100");
        helper.assertTrue(saves.get() == 1, "Save callback signaled once on modulate insert");

        // SIMULATE must not modify item components or trigger save callback
        long simAqua = cell.insert(aqua, 50, Actionable.SIMULATE, SOURCE);
        helper.assertTrue(simAqua == 50, "Simulate insert returns potential accepted count");
        helper.assertTrue(saves.get() == 1, "Save callback NOT called on SIMULATE");
        helper.assertTrue(cell.ledger().contents().amounts().size() == 1, "SIMULATE does not add aspect to stored amounts");

        // Fill up to 12 types in the 64k cell
        String[] aspects = {"terra", "ignis", "ordo", "perditio", "metallum", "motus", "praecantatio", "sensus", "spiritus", "vacuos", "vitreus"};
        for (String tag : aspects) {
            long acc = cell.insert(EssentiaKey.ofTag(tag), 10, Actionable.MODULATE, SOURCE);
            helper.assertTrue(acc == 10, "Accepted aspect " + tag);
        }
        helper.assertTrue(cell.ledger().usedTypes() == 12, "Cell currently has 12 types occupied");

        // 13th type must be rejected when types are full
        EssentiaKey thirteeth = EssentiaKey.ofTag("volatus");
        long acc13 = cell.insert(thirteeth, 10, Actionable.MODULATE, SOURCE);
        helper.assertTrue(acc13 == 0, "13th type rejected because 12 types limit reached");

        // Inserting more of an ALREADY STORED aspect does not consume a new type slot
        long moreAer = cell.insert(aer, 50, Actionable.MODULATE, SOURCE);
        helper.assertTrue(moreAer == 50, "Inserting more of existing type succeeds without new type slot");
        helper.assertTrue(cell.ledger().contents().amounts().get("aer") == 150, "Aer total amount is 150");

        // Task 5: Extraction
        helper.assertTrue(cell.extract(aer, 0, Actionable.MODULATE, SOURCE) == 0, "Extract 0 returns 0");
        helper.assertTrue(cell.extract(aer, -10, Actionable.MODULATE, SOURCE) == 0, "Extract negative returns 0");

        // SIMULATE extract must not modify components or trigger saves
        int saveCountBeforeSim = saves.get();
        long simExtracted = cell.extract(aer, 50, Actionable.SIMULATE, SOURCE);
        helper.assertTrue(simExtracted == 50, "Simulated extract reports 50");
        helper.assertTrue(cell.ledger().contents().amounts().get("aer") == 150, "Aer amount unchanged after SIMULATE extract");
        helper.assertTrue(saves.get() == saveCountBeforeSim, "Save callback NOT invoked during SIMULATE extract");

        // Partial extract
        long partial = cell.extract(aer, 50, Actionable.MODULATE, SOURCE);
        helper.assertTrue(partial == 50 && cell.ledger().contents().amounts().get("aer") == 100, "Partial extract leaves 100");

        // Excess extract (request 500 when only 100 stored)
        long excess = cell.extract(aer, 500, Actionable.MODULATE, SOURCE);
        helper.assertTrue(excess == 100, "Excess extract capped to available amount 100");
        helper.assertTrue(!cell.ledger().contents().amounts().containsKey("aer"), "Aer slot is completely freed");
        helper.assertTrue(cell.ledger().usedTypes() == 11, "Type count decremented to 11");

        // Hole reuse: freed slot index is replaced by next newly inserted aspect
        helper.assertTrue(cell.ledger().contents().slots().contains(""), "Slot list retains hole for freed aspect");
        long insertedVolatus = cell.insert(thirteeth, 25, Actionable.MODULATE, SOURCE);
        helper.assertTrue(insertedVolatus == 25, "13th aspect now accepted because a slot was freed");
        helper.assertTrue(cell.ledger().contents().slots().get(0).equals("volatus"), "Newly inserted aspect reuses hole at index 0");

        helper.succeed();
    }

    /**
     * Task 6: Unknown aspects and custom data retention, preferred storage logic.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void test6_unknownAspectsAndPreferredStorage(GameTestHelper helper) {
        VerificationReports.install();

        // Unknown addon aspect tag must not be dropped during read/write
        String customTag = "customaddon:superaspect";
        CellContents contents = new CellContents(Map.of(customTag, 500L), List.of(customTag), List.of(customTag));
        helper.assertTrue(contents.amounts().containsKey(customTag), "Custom aspect tag retained in amounts");
        helper.assertTrue(contents.partitions().contains(customTag), "Custom aspect tag retained in partitions");
        helper.assertTrue(contents.slots().contains(customTag), "Custom aspect tag retained in slots");

        ItemStack stack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K4).get());
        stack.set(ThaumicEnergistics.CELL_CONTENTS, contents);

        EssentiaCell cell = new EssentiaCell(stack, null);
        KeyCounter counter = new KeyCounter();
        cell.getAvailableStacks(counter);

        EssentiaKey customKey = EssentiaKey.ofTag(customTag);
        helper.assertTrue(counter.get(customKey) == 500L, "Unknown aspect reported in available stacks with correct amount");
        helper.assertTrue(cell.isPreferredStorageFor(customKey, SOURCE), "Cell is preferred storage for partitioned custom aspect");

        // Partitioned cell rejects unpartitioned aspect from preferred
        EssentiaKey unpartitioned = EssentiaKey.of(Aspects.FIRE);
        helper.assertTrue(!cell.isPreferredStorageFor(unpartitioned, SOURCE), "Unpartitioned aspect is not preferred in partitioned cell");

        helper.succeed();
    }

    /**
     * Task 7: Defensive snapshots: external collections cannot mutate cell contents.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void test7_defensiveSnapshots(GameTestHelper helper) {
        VerificationReports.install();

        List<String> mutablePartitions = new ArrayList<>();
        mutablePartitions.add("aer");
        mutablePartitions.add("aqua");

        CellContents contents = new CellContents(Map.of("aer", 10L), mutablePartitions);

        // Verify defensive copy on creation
        mutablePartitions.add("ignis");
        helper.assertTrue(contents.partitions().size() == 2, "Mutating external list after constructor does not affect CellContents");

        // Verify unmodifiable getters
        try {
            contents.partitions().add("terra");
            helper.fail("Expected UnsupportedOperationException when mutating partitions list");
        } catch (UnsupportedOperationException expected) {}

        try {
            contents.amounts().put("terra", 5L);
            helper.fail("Expected UnsupportedOperationException when mutating amounts map");
        } catch (UnsupportedOperationException expected) {}

        helper.succeed();
    }

    /**
     * Task 8: Invalidation and save callback safety. Replaced/invalid stack cannot be written to.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void test8_saveCallbacksAndInvalidation(GameTestHelper helper) {
        VerificationReports.install();

        ItemStack stack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());
        AtomicInteger saves = new AtomicInteger();
        EssentiaCell cell = new EssentiaCell(stack, saves::incrementAndGet);

        EssentiaKey aer = EssentiaKey.of(Aspects.AIR);
        cell.insert(aer, 10, Actionable.MODULATE, SOURCE);
        helper.assertTrue(saves.get() == 1, "Save callback triggered on insert");

        // Invalidate cell handler
        cell.invalidate();
        helper.assertTrue(!cell.isValid(), "Cell is now marked invalid");
        long acceptedAfterInvalid = cell.insert(aer, 10, Actionable.MODULATE, SOURCE);
        helper.assertTrue(acceptedAfterInvalid == 0, "Invalid cell handler rejects insertion");
        long extractedAfterInvalid = cell.extract(aer, 5, Actionable.MODULATE, SOURCE);
        helper.assertTrue(extractedAfterInvalid == 0, "Invalid cell handler rejects extraction");
        helper.assertTrue(saves.get() == 1, "Save callback not invoked after invalidation");

        helper.succeed();
    }

    /**
     * Task 9: Creative cell rules: zero drain, no accumulation, display amount, partition-only extraction.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void test9_creativeCellSemantics(GameTestHelper helper) {
        VerificationReports.install();

        ItemStack stack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.CREATIVE).get());
        EssentiaCell cell = new EssentiaCell(stack, null);

        EssentiaKey fire = EssentiaKey.of(Aspects.FIRE);
        EssentiaKey water = EssentiaKey.of(Aspects.WATER);

        // Creative cell rejects all inserts (no accumulation)
        long inserted = cell.insert(fire, 100, Actionable.MODULATE, SOURCE);
        helper.assertTrue(inserted == 0, "Creative cell rejects insertion (returns 0 accepted)");
        helper.assertTrue(cell.ledger().total() == 0, "Creative cell total essentia remains 0");
        helper.assertTrue(cell.ledger().usedBytes() == 0, "Creative cell used bytes is 0");
        helper.assertTrue(cell.getIdleDrain() == 0.0, "Creative cell idle drain is 0.0");
        helper.assertTrue(!cell.canFitInsideCell(), "Creative cell cannot fit inside another cell");

        // Unpartitioned creative cell provides nothing
        KeyCounter counter = new KeyCounter();
        cell.getAvailableStacks(counter);
        helper.assertTrue(counter.isEmpty(), "Unpartitioned creative cell advertises nothing");
        helper.assertTrue(cell.extract(fire, 100, Actionable.MODULATE, SOURCE) == 0, "Cannot extract unpartitioned aspect from creative cell");

        // Configure partition for FIRE
        cell.partitions(List.of("ignis"));
        counter.clear();
        cell.getAvailableStacks(counter);
        helper.assertTrue(counter.get(fire) == CellLedger.CREATIVE_DISPLAY_AMOUNT, "Creative cell advertises 2,000,000,000 for partitioned aspect");

        // Extraction provides exact requested amount without consuming stored units
        long extracted = cell.extract(fire, 1000000L, Actionable.MODULATE, SOURCE);
        helper.assertTrue(extracted == 1000000L, "Creative cell extracts full requested amount");
        helper.assertTrue(cell.ledger().total() == 0, "Creative cell total remains 0 after extraction");
        helper.assertTrue(cell.extract(water, 10, Actionable.MODULATE, SOURCE) == 0, "Non-partitioned aspect extracts 0 from creative cell");

        helper.succeed();
    }

    /**
     * Task 10: Disassembly into component and casing on sneaking right click when empty.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void test10_disassemblyAndEmptyCheck(GameTestHelper helper) {
        VerificationReports.install();

        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        var level = helper.getLevel();

        // 1. Creative cell cannot be disassembled
        ItemStack creativeStack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.CREATIVE).get());
        player.setItemInHand(InteractionHand.MAIN_HAND, creativeStack);
        player.setShiftKeyDown(true);
        var creativeItem = (EssentiaCellItem) creativeStack.getItem();
        var resCreative = creativeItem.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(resCreative.getResult().consumesAction() == false, "Creative cell refuses disassembly");

        // 2. Non-empty cell cannot be disassembled (contents preserved)
        ItemStack populatedStack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());
        EssentiaCell popCell = new EssentiaCell(populatedStack, null);
        popCell.insert(EssentiaKey.of(Aspects.AIR), 20, Actionable.MODULATE, SOURCE);
        player.setItemInHand(InteractionHand.MAIN_HAND, populatedStack);
        player.setShiftKeyDown(true);
        var popItem = (EssentiaCellItem) populatedStack.getItem();
        var resPop = popItem.use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(resPop.getResult().consumesAction() == false, "Non-empty cell refuses disassembly");
        helper.assertTrue(new EssentiaCell(player.getItemInHand(InteractionHand.MAIN_HAND), null).ledger().total() == 20, "Contents intact");

        // 3. Not sneaking refuses disassembly
        ItemStack emptyStack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());
        player.setItemInHand(InteractionHand.MAIN_HAND, emptyStack);
        player.setShiftKeyDown(false);
        var resNotSneak = ((EssentiaCellItem) emptyStack.getItem()).use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(resNotSneak.getResult().consumesAction() == false, "Not sneaking refuses disassembly");

        // 4. Empty cell + sneaking right click disassembles successfully
        player.setShiftKeyDown(true);
        var resEmpty = ((EssentiaCellItem) emptyStack.getItem()).use(level, player, InteractionHand.MAIN_HAND);
        helper.assertTrue(resEmpty.getResult().consumesAction(), "Empty cell disassembles on sneaking right click");
        helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND).is(ThaumicEnergistics.COMPONENT_ITEMS.get(CellTier.K1).get()),
                "Hand item converted to 1k storage component");
        helper.assertTrue(player.getInventory().contains(new ItemStack(ThaumicEnergistics.STORAGE_CASING.get())),
                "Storage casing deposited into player inventory");

        helper.succeed();
    }

    /**
     * Task 11: Tooltips formatting, bytes, types, partitions, colors, shift toggle.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void test11_tooltipsFormattingAndColors(GameTestHelper helper) {
        VerificationReports.install();

        ItemStack stack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());
        EssentiaCell cell = new EssentiaCell(stack, null);
        cell.insert(EssentiaKey.of(Aspects.AIR), 5, Actionable.MODULATE, SOURCE);
        cell.partitions(List.of("aer", "aqua", "ignis"));

        List<Component> tooltip = new ArrayList<>();
        stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);

        // Bytes line: "3 of 1024 bytes used." (5 essentia / 2 = 3 bytes ceiling)
        helper.assertTrue(tooltip.size() >= 3, "Tooltip contains bytes, types, and partitioned lines");
        String bytesStr = tooltip.get(0).getString();
        helper.assertTrue(bytesStr.contains("3") && bytesStr.contains("1024"), "Tooltip reports 3 of 1024 bytes used: " + bytesStr);

        // Types line: "1 of 12 essentia types stored." (occupied types = 1, NOT partition count 3!)
        String typesStr = tooltip.get(1).getString();
        helper.assertTrue(typesStr.contains("1") && typesStr.contains("12"), "Occupied types is 1, not confused with whitelist count: " + typesStr);

        // Partitioned line present
        String partStr = tooltip.get(2).getString();
        helper.assertTrue(partStr.contains("Partitioned"), "Partitioned status displayed: " + partStr);

        // Primal aspect colors check
        helper.assertTrue(EssentiaCellItem.getAspectChatColor(Aspects.AIR) == ChatFormatting.YELLOW, "Air is yellow");
        helper.assertTrue(EssentiaCellItem.getAspectChatColor(Aspects.WATER) == ChatFormatting.AQUA, "Water is aqua");
        helper.assertTrue(EssentiaCellItem.getAspectChatColor(Aspects.FIRE) == ChatFormatting.RED, "Fire is red");
        helper.assertTrue(EssentiaCellItem.getAspectChatColor(Aspects.ORDER) == ChatFormatting.GRAY, "Order is gray");
        helper.assertTrue(EssentiaCellItem.getAspectChatColor(Aspects.ENTROPY) == ChatFormatting.DARK_GRAY, "Entropy is dark gray");
        helper.assertTrue(EssentiaCellItem.getAspectChatColor(Aspects.EARTH) == ChatFormatting.GREEN, "Earth is green");

        helper.succeed();
    }

    /**
     * Task 12: Integer/long arithmetic, usedBytes ceiling rounding, Long.MAX_VALUE overflow protection.
     */
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void test12_integerLongArithmeticAndLimits(GameTestHelper helper) {
        VerificationReports.install();

        // usedBytes ceiling rounding: 0->0, 1->1, 2->1, 3->2, 4->2
        CellLedger ledger0 = new CellLedger(CellTier.K1, CellContents.EMPTY);
        helper.assertTrue(ledger0.usedBytes() == 0, "0 essentia -> 0 bytes");

        CellLedger ledger1 = new CellLedger(CellTier.K1, new CellContents(Map.of("aer", 1L), List.of()));
        helper.assertTrue(ledger1.usedBytes() == 1, "1 essentia -> 1 byte");

        CellLedger ledger2 = new CellLedger(CellTier.K1, new CellContents(Map.of("aer", 2L), List.of()));
        helper.assertTrue(ledger2.usedBytes() == 1, "2 essentia -> 1 byte");

        CellLedger ledger3 = new CellLedger(CellTier.K1, new CellContents(Map.of("aer", 3L), List.of()));
        helper.assertTrue(ledger3.usedBytes() == 2, "3 essentia -> 2 bytes");

        CellLedger ledger4 = new CellLedger(CellTier.K1, new CellContents(Map.of("aer", 4L), List.of()));
        helper.assertTrue(ledger4.usedBytes() == 2, "4 essentia -> 2 bytes");

        // Long.MAX_VALUE requested insert does not wrap around and respects cell capacity
        long accepted = ledger0.insert("aer", Long.MAX_VALUE, false);
        helper.assertTrue(accepted == CellTier.K1.capacity(), "Long.MAX_VALUE insert capped to exact cell capacity 2048");

        // Overcapacity cell protection: existing overcapacity data retained, further inserts rejected
        CellLedger overcapacityLedger = new CellLedger(CellTier.K1, new CellContents(Map.of("aer", 50000L), List.of()));
        helper.assertTrue(overcapacityLedger.total() == 50000L, "Overcapacity total retained intact");
        long overAccepted = overcapacityLedger.insert("aer", 10, true);
        helper.assertTrue(overAccepted == 0, "Overcapacity cell rejects any new inserts (returns 0 accepted)");

        // Long.MAX_VALUE total summation safety
        CellLedger hugeLedger = new CellLedger(CellTier.K1, new CellContents(Map.of("aer", Long.MAX_VALUE - 10, "terra", 100L), List.of()));
        helper.assertTrue(hugeLedger.total() == Long.MAX_VALUE, "Huge totals clamp to Long.MAX_VALUE without wrap-around");
        helper.assertTrue(hugeLedger.usedBytes() > 0, "usedBytes does not overflow into negative numbers");

        helper.succeed();
    }
}
