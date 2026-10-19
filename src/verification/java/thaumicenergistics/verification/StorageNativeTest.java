package thaumicenergistics.verification;

import appeng.api.config.Actionable;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyTypes;
import appeng.api.storage.StorageCells;
import appeng.api.storage.cells.CellState;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.Aspects;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.storage.CellContents;
import thaumicenergistics.storage.CellTier;
import thaumicenergistics.storage.EssentiaCell;
import thaumicenergistics.storage.EssentiaKey;
import thaumicenergistics.storage.EssentiaKeyType;
import thaumicenergistics.storage.ContainerStorage;
import thaumicenergistics.storage.EssentiaTransfers;
import thaumicenergistics.storage.PoweredEssentiaStorage;

@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class StorageNativeTest {
    private static final IActionSource SOURCE = IActionSource.empty();

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeContainersAndPartialTransfers(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(2, 2, 2));
        level.setBlockAndUpdate(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("thaumcraft:warded_jar")).defaultBlockState());
        var jar = level.getCapability(thaumcraft.api.aspects.EssentiaCapabilities.CONTAINER, pos, null);
        helper.assertTrue(jar != null, "native Thaumcraft jar exposes public container capability");
        jar.setAspects(new thaumcraft.api.aspects.AspectList().add(Aspects.WATER, 64));
        var external = ContainerStorage.at(level, pos, false);
        var water = EssentiaKey.of(Aspects.WATER);
        var earth = EssentiaKey.of(Aspects.EARTH);
        var stack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());
        var cell = new EssentiaCell(stack, null);
        cell.insert(earth, 2047, Actionable.MODULATE, SOURCE);
        helper.assertTrue(EssentiaTransfers.importFrom(external, cell, water, 32, SOURCE) == 1, "partial import into one remaining cell unit");
        helper.assertTrue(jar.containerContains(Aspects.WATER) == 63 && cell.ledger().total() == 2048, "approved fix: only one source unit removed, not requested 32");
        helper.assertTrue(EssentiaTransfers.importFrom(external, cell, water, 32, SOURCE) == 0 && jar.containerContains(Aspects.WATER) == 63, "full network leaves source untouched");
        helper.assertTrue(EssentiaTransfers.exportTo(cell, external, water, 32, SOURCE) == 1, "export limited to exact jar space");
        helper.assertTrue(jar.containerContains(Aspects.WATER) == 64 && cell.ledger().total() == 2047, "export conserves units");
        helper.assertTrue(external.insert(earth, 1, Actionable.MODULATE, SOURCE) == 0, "mixed jar aspects rejected");
        cell.insert(water, 1, Actionable.MODULATE, SOURCE);
        var budget = new double[]{0.29};
        appeng.api.networking.energy.IEnergySource energy = (requested, mode, multiplier) -> {
            double provided = Math.min(requested, budget[0]);
            if (mode == Actionable.MODULATE) budget[0] -= provided;
            return provided;
        };
        var powered = new PoweredEssentiaStorage(cell, energy);
        helper.assertTrue(powered.extract(water, 1, Actionable.MODULATE, SOURCE) == 0 && cell.ledger().total() == 2048, "insufficient 0.3 AE budget prevents transfer");
        budget[0] = 1;
        helper.assertTrue(powered.extract(water, 1, Actionable.SIMULATE, SOURCE) == 1 && budget[0] == 1, "power and resource simulation do not mutate");
        helper.assertTrue(powered.extract(water, 1, Actionable.MODULATE, SOURCE) == 1 && Math.abs(budget[0] - 0.7) < 1e-9, "original 0.3 AE per committed unit");
        var voidPos = pos.east(2);
        level.setBlockAndUpdate(voidPos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("thaumcraft:void_jar")).defaultBlockState());
        var voidJar = level.getCapability(thaumcraft.api.aspects.EssentiaCapabilities.CONTAINER, voidPos, null);
        voidJar.setAspects(new thaumcraft.api.aspects.AspectList().add(Aspects.WATER, 64));
        helper.assertTrue(ContainerStorage.at(level, voidPos, false).insert(water, 8, Actionable.MODULATE, SOURCE) == 0, "default does not void overflow");
        helper.assertTrue(ContainerStorage.at(level, voidPos, true).insert(water, 8, Actionable.SIMULATE, SOURCE) == 8 && voidJar.containerContains(Aspects.WATER) == 64, "explicit void simulation");
        helper.assertTrue(ContainerStorage.at(level, voidPos, true).insert(water, 8, Actionable.MODULATE, SOURCE) == 8 && voidJar.containerContains(Aspects.WATER) == 64, "explicit void overflow matches original mode");
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(voidPos, Blocks.AIR.defaultBlockState());
        System.out.println("THE_NATIVE_CONTAINER_TRANSFERS_PASS");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void keysAndPersistentCells(GameTestHelper helper) {
        VerificationReports.install();
        var registries = helper.getLevel().registryAccess();
        var fire = EssentiaKey.of(Aspects.FIRE);
        helper.assertTrue(AEKeyTypes.get(ThaumicEnergistics.id("essentia")) == EssentiaKeyType.INSTANCE, "custom key type registered in actual AE2 registry");
        helper.assertTrue(fire.equals(EssentiaKey.ofTag("thaumcraft:ignis")), "canonical key identity");
        helper.assertTrue(fire.getPrimaryKey() == EssentiaKey.ofTag("thaumcraft:ignis").getPrimaryKey(), "AE2 primary-key reference identity");
        var counter = new appeng.api.stacks.KeyCounter();
        counter.add(fire, 17);
        helper.assertTrue(counter.get(EssentiaKey.ofTag("ignis")) == 17, "separately reconstructed keys share AE2 counter partition");
        helper.assertTrue(fire.equals(AEKey.fromTagGeneric(registries, fire.toTagGeneric(registries))), "generic AEKey NBT round trip");
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), registries);
        try {
            AEKey.writeKey(buffer, fire);
            helper.assertTrue(fire.equals(AEKey.readKey(buffer)), "actual network key codec");
        } finally { buffer.release(); }
        for (var tier : CellTier.values()) {
            var stack = new ItemStack(ThaumicEnergistics.CELLS.get(tier).get());
            var saves = new AtomicInteger();
            var cell = (EssentiaCell)StorageCells.getCellInventory(stack, saves::incrementAndGet);
            helper.assertTrue(cell != null && cell.getIdleDrain() == tier.idleDrain, "AE2 recognizes cell and original idle drain");
            helper.assertTrue(cell.insert(AEItemKey.of(Items.DIAMOND), 1, Actionable.MODULATE, SOURCE) == 0, "reject item resource");
            helper.assertTrue(cell.insert(fire, 17, Actionable.SIMULATE, SOURCE) == (tier.creative() ? 0 : 17), "native insertion simulation");
            helper.assertTrue(!stack.has(ThaumicEnergistics.CELL_CONTENTS) && saves.get() == 0, "simulation does not write item data or mark dirty");
            if (tier.creative()) {
                cell.partitions(List.of("ignis"));
                helper.assertTrue(cell.extract(fire, 123456789, Actionable.MODULATE, SOURCE) == 123456789, "configured creative source");
            } else {
                helper.assertTrue(cell.insert(fire, 17, Actionable.MODULATE, SOURCE) == 17 && saves.get() == 1, "native commit and save callback");
                helper.assertTrue(!cell.canFitInsideCell(), "populated cells cannot nest");
                // Separate wrappers on the same stack must not retain stale mutable caches.
                var second = (EssentiaCell)StorageCells.getCellInventory(stack, null);
                helper.assertTrue(second.extract(fire, 5, Actionable.MODULATE, SOURCE) == 5, "second wrapper extracts actual data");
                helper.assertTrue(cell.extract(fire, 100, Actionable.SIMULATE, SOURCE) == 12, "first wrapper sees current data");
                var copied = stack.copy();
                second.extract(fire, 1, Actionable.MODULATE, SOURCE);
                helper.assertTrue(((EssentiaCell)StorageCells.getCellInventory(copied, null)).ledger().total() == 12, "ItemStack copy owns immutable component snapshot");
            }
            var saved = stack.save(registries);
            var loaded = ItemStack.parse(registries, saved).orElseThrow();
            helper.assertTrue(stack.get(ThaumicEnergistics.CELL_CONTENTS).equals(loaded.get(ThaumicEnergistics.CELL_CONTENTS)), "actual ItemStack NBT persistence");
        }
        System.out.println("THE_NATIVE_CELLS_PASS");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void poweredDriveStoresEssentia(GameTestHelper helper) {
        VerificationReports.install();
        var drivePos = helper.absolutePos(new BlockPos(2, 2, 2));
        var energyPos = drivePos.east();
        var level = helper.getLevel();
        level.setBlockAndUpdate(drivePos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:drive")).defaultBlockState());
        level.setBlockAndUpdate(energyPos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState());
        var slots = level.getCapability(Capabilities.ItemHandler.BLOCK, drivePos, Direction.UP);
        helper.assertTrue(slots != null, "actual AE2 drive exposes its cell slots");
        helper.assertTrue(slots.insertItem(0, new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get()), false).isEmpty(), "actual drive accepts essentia cell");
        helper.runAfterDelay(40, () -> {
            var drive = level.getBlockEntity(drivePos);
            helper.assertTrue(drive instanceof IInWorldGridNodeHost, "actual drive hosts an AE2 node");
            var node = ((IInWorldGridNodeHost)drive).getGridNode(Direction.UP);
            helper.assertTrue(node != null && node.isActive(), "drive active in powered actual AE2 grid");
            var storage = node.getGrid().getStorageService().getInventory();
            var water = EssentiaKey.of(Aspects.WATER);
            helper.assertTrue(storage.insert(water, 500, Actionable.MODULATE, SOURCE) == 500, "grid inserts custom resource into cell");
            helper.assertTrue(storage.extract(water, 111, Actionable.SIMULATE, SOURCE) == 111, "grid extraction simulation");
            helper.assertTrue(slots.getStackInSlot(0).getOrDefault(ThaumicEnergistics.CELL_CONTENTS, CellContents.EMPTY).amounts().get("aqua") == 500,
                    "simulation retains all units in actual drive cell");
            long listed = storage.getAvailableStacks().get(water);
            helper.assertTrue(listed == 500, "network enumeration uses canonical aspect identity: " + listed);
            helper.assertTrue(storage.extract(water, 111, Actionable.MODULATE, SOURCE) == 111, "grid extraction commit");
            var removed = slots.extractItem(0, 1, false);
            helper.assertTrue(!removed.isEmpty(), "cell can be removed from actual drive");
            helper.assertTrue(removed.getOrDefault(ThaumicEnergistics.CELL_CONTENTS, CellContents.EMPTY).amounts().get("aqua") == 389, "removed physical cell preserves exact balance");
            var saved = removed.save(level.registryAccess());
            var restored = ItemStack.parse(level.registryAccess(), saved).orElseThrow();
            helper.assertTrue(slots.insertItem(0, restored, false).isEmpty(), "restored physical cell reinserts");
            level.setBlockAndUpdate(energyPos, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(drivePos, Blocks.AIR.defaultBlockState());
            System.out.println("THE_POWERED_DRIVE_PASS balance=389");
            helper.succeed();
        });
    }
}
