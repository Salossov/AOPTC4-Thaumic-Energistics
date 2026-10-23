package thaumicenergistics.verification;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.energy.IAEPowerStorage;
import appeng.api.networking.security.IActionSource;
import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.Aspects;
import thaumcraft.api.aspects.EssentiaCapabilities;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.parts.EssentiaStorageBusPart;
import thaumicenergistics.storage.CellTier;
import thaumicenergistics.storage.EssentiaCell;
import thaumicenergistics.storage.EssentiaKey;

/**
 * Deferred verification suite for Tasks 25-42 (Batch 5 Parts C & D).
 * NOTE: DO NOT EXECUTE - Verification deferred by user request.
 */
@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class StorageBusBatch5Test {

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void finitePowerAndDebit(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var cable = helper.absolutePos(new BlockPos(3, 2, 3));
        var target = cable.south();
        var batteryPos = cable.west();

        level.setBlockAndUpdate(batteryPos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:energy_cell")).defaultBlockState());
        ((IAEPowerStorage) level.getBlockEntity(batteryPos)).injectAEPower(1000, Actionable.MODULATE);
        level.setBlockAndUpdate(target, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("thaumcraft:warded_jar")).defaultBlockState());

        var jar = level.getCapability(EssentiaCapabilities.CONTAINER, target, null);
        jar.setAspects(new AspectList().add(Aspects.WATER, 8));

        PartHelper.setPart(level, cable, null, null, IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        var bus = (EssentiaStorageBusPart) PartHelper.setPart(level, cable, Direction.SOUTH, null, ThaumicEnergistics.STORAGE_BUS.get());

        helper.runAfterDelay(50, () -> {
            helper.assertTrue(bus.getMainNode().isActive(), "finite battery boots storage bus node");
            var energy = bus.getMainNode().getGrid().getEnergyService();
            var water = EssentiaKey.of(Aspects.WATER);
            var source = IActionSource.empty();
            var multiplier = PowerMultiplier.CONFIG;

            double before = energy.extractAEPower(1e9, Actionable.SIMULATE, multiplier);
            helper.assertTrue(bus.storage().extract(water, 1000, Actionable.SIMULATE, source) == 8
                    && jar.containerContains(Aspects.WATER) == 8
                    && Math.abs(energy.extractAEPower(1e9, Actionable.SIMULATE, multiplier) - before) < 1e-7,
                    "storage bus simulates power for available 8 units, not whole 1000-unit request");

            helper.assertTrue(bus.storage().extract(water, 1000, Actionable.MODULATE, source) == 8
                    && Math.abs(before - energy.extractAEPower(1e9, Actionable.SIMULATE, multiplier) - 2.4) < 1e-7,
                    "actual finite grid debits original 0.3 AE per extracted unit");

            before = energy.extractAEPower(1e9, Actionable.SIMULATE, multiplier);
            helper.assertTrue(bus.storage().insert(water, 2, Actionable.MODULATE, source) == 2
                    && Math.abs(before - energy.extractAEPower(1e9, Actionable.SIMULATE, multiplier) - 0.6) < 1e-7,
                    "actual finite grid debits 0.3 AE per inserted unit");

            energy.extractAEPower(1e9, Actionable.MODULATE, multiplier);
            energy.injectPower(0.29, Actionable.MODULATE);
            helper.assertTrue(bus.storage().extract(water, 1, Actionable.MODULATE, source) == 0
                    && jar.containerContains(Aspects.WATER) == 2,
                    "insufficient 0.29 AE leaves jar untouched");

            energy.injectPower(0.32, Actionable.MODULATE);
            helper.assertTrue(bus.storage().extract(water, 2, Actionable.MODULATE, source) == 2
                    && jar.containerContains(Aspects.WATER) == 0,
                    "refilling to 0.61 AE permits two actual units without rounding loss");

            for (var pos : java.util.List.of(cable, target, batteryPos)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            System.out.println("THE_B5_STORAGE_BUS_POWER_PASS");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void priorityOrderingAndTieBreaking(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var drive = helper.absolutePos(new BlockPos(2, 2, 2));
        var cable = drive.south();
        var target = cable.south();

        level.setBlockAndUpdate(drive, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:drive")).defaultBlockState());
        level.setBlockAndUpdate(drive.west(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState());
        level.setBlockAndUpdate(target, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("thaumcraft:warded_jar")).defaultBlockState());

        var jar = level.getCapability(EssentiaCapabilities.CONTAINER, target, null);
        var driveSlots = level.getCapability(Capabilities.ItemHandler.BLOCK, drive, Direction.UP);
        var cellItem = ThaumicEnergistics.CELLS.get(CellTier.K1).get();
        driveSlots.insertItem(0, new ItemStack(cellItem), false);

        PartHelper.setPart(level, cable, null, null, IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        var bus = (EssentiaStorageBusPart) PartHelper.setPart(level, cable, Direction.SOUTH, null, ThaumicEnergistics.STORAGE_BUS.get());
        bus.priority(10);

        helper.runAfterDelay(50, () -> {
            var storage = bus.getMainNode().getGrid().getStorageService().getInventory();
            var water = EssentiaKey.of(Aspects.WATER);
            var source = IActionSource.empty();

            helper.assertTrue(storage.insert(water, 100, Actionable.MODULATE, source) == 100
                    && jar.containerContains(Aspects.WATER) == 64
                    && new EssentiaCell(driveSlots.getStackInSlot(0), null).ledger().total() == 36,
                    "positive-priority jar fills before lower-priority drive and preserves remainder");

            jar.setAspects(new AspectList());
            driveSlots.extractItem(0, 1, false);
            driveSlots.insertItem(0, new ItemStack(cellItem), false);
            bus.priority(-10);

            helper.assertTrue(storage.insert(water, 20, Actionable.MODULATE, source) == 20
                    && jar.containerContains(Aspects.WATER) == 0
                    && new EssentiaCell(driveSlots.getStackInSlot(0), null).ledger().total() == 20,
                    "negative-priority bus yields to zero-priority drive after remount");

            for (var pos : java.util.List.of(cable, target, drive, drive.west())) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
            System.out.println("THE_B5_STORAGE_BUS_PRIORITY_PASS");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 160)
    public static void condenserRoutingAndMatterProduction(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var cable = helper.absolutePos(new BlockPos(3, 2, 3));
        var target = cable.south();

        level.setBlockAndUpdate(cable.west(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState());
        level.setBlockAndUpdate(target, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:condenser")).defaultBlockState());

        var condenser = (appeng.blockentity.misc.CondenserBlockEntity) level.getBlockEntity(target);
        var component = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:cell_component_1k")).getDefaultInstance();
        helper.assertTrue(condenser.getInternalInventory().insertItem(2, component, false).isEmpty() && condenser.getStorage() > 0, "condenser has matter storage component");
        condenser.getConfigManager().putSetting(appeng.api.config.Settings.CONDENSER_OUTPUT, appeng.api.config.CondenserOutput.MATTER_BALLS);

        PartHelper.setPart(level, cable, null, null, IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        var bus = (EssentiaStorageBusPart) PartHelper.setPart(level, cable, Direction.SOUTH, null, ThaumicEnergistics.STORAGE_BUS.get());

        helper.runAfterDelay(50, () -> {
            var storage = bus.getMainNode().getGrid().getStorageService().getInventory();
            var water = EssentiaKey.of(Aspects.WATER);
            var source = IActionSource.empty();

            helper.assertTrue(storage.insert(water, 17, Actionable.SIMULATE, source) == 17 && condenser.getStoredPower() == 0,
                    "condenser simulation accepts without creating matter");
            helper.assertTrue(storage.insert(water, 17, Actionable.MODULATE, source) == 17 && condenser.getStoredPower() == 17,
                    "original 500/500 conversion gives 1 native matter per essentia");
            helper.assertTrue(storage.extract(water, 1, Actionable.MODULATE, source) == 0 && storage.getAvailableStacks().get(water) == 0,
                    "condenser never advertises or extracts essentia");

            bus.filter(0, EssentiaKey.of(Aspects.FIRE));
            helper.assertTrue(storage.insert(water, 1, Actionable.MODULATE, source) == 0 && condenser.getStoredPower() == 17,
                    "condenser route honors aspect whitelist");

            bus.getUpgrades().insertItem(0, BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:inverter_card")).getDefaultInstance(), false);
            helper.assertTrue(storage.insert(water, 239, Actionable.MODULATE, source) == 239,
                    "inverter permits unlisted essentia into condenser");
            helper.assertTrue(condenser.getInternalInventory().getStackInSlot(1).is(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:matter_ball"))) && condenser.getStoredPower() == 0,
                    "256 essentia produce native matter ball");

            level.setBlockAndUpdate(cable, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(cable.west(), Blocks.AIR.defaultBlockState());
            System.out.println("THE_B5_STORAGE_BUS_CONDENSER_PASS");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void interfaceSubnetAndCyclePrevention(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var cable = helper.absolutePos(new BlockPos(2, 2, 2));
        var target = cable.south();
        var drive = target.south();
        var aeCable = IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable"));

        level.setBlockAndUpdate(cable.west(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState());
        level.setBlockAndUpdate(target, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:interface")).defaultBlockState());
        level.setBlockAndUpdate(drive, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:drive")).defaultBlockState());
        level.setBlockAndUpdate(drive.east(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState());

        PartHelper.setPart(level, target.east(), null, null, aeCable);
        var cell = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());
        var water = EssentiaKey.of(Aspects.WATER);
        var source = IActionSource.empty();
        new EssentiaCell(cell, null).insert(water, 30, Actionable.MODULATE, source);
        level.getCapability(Capabilities.ItemHandler.BLOCK, drive, Direction.UP).insertItem(0, cell, false);

        PartHelper.setPart(level, cable, null, null, aeCable);
        var bus = (EssentiaStorageBusPart) PartHelper.setPart(level, cable, Direction.SOUTH, null, ThaumicEnergistics.STORAGE_BUS.get());

        helper.runAfterDelay(60, () -> {
            var grid = bus.getMainNode().getGrid();
            var targetNode = ((appeng.helpers.InterfaceLogicHost) level.getBlockEntity(target)).getInterfaceLogic().getActionableNode();
            helper.assertTrue(targetNode != null && targetNode.isActive() && targetNode.getGrid() != grid,
                    "facing interface belongs to separate actual AE2 network");

            var storage = grid.getStorageService().getInventory();
            helper.assertTrue(storage.getAvailableStacks().get(water) == 30, "interface capability exposes remote cell contents");

            bus.filter(0, EssentiaKey.of(Aspects.FIRE));
            helper.assertTrue(storage.extract(water, 5, Actionable.MODULATE, source) == 5,
                    "original interface route ignores bus aspect whitelist");
            helper.assertTrue(storage.insert(water, 9, Actionable.MODULATE, source) == 9
                    && targetNode.getGrid().getStorageService().getInventory().getAvailableStacks().get(water) == 34,
                    "bridge insert updates remote cell without phantom units");

            var returnTarget = cable.east();
            var returnCable = target.east();
            level.setBlockAndUpdate(returnTarget, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:interface")).defaultBlockState());
            var returning = (EssentiaStorageBusPart) PartHelper.setPart(level, returnCable, Direction.NORTH, null, ThaumicEnergistics.STORAGE_BUS.get());

            helper.runAfterDelay(45, () -> {
                helper.assertTrue(returning.getMainNode().isActive() && returning.getMainNode().getGrid() != grid,
                        "return link boots on remote network");
                helper.assertTrue(storage.getAvailableStacks().get(water) == 34
                        && returning.getMainNode().getGrid().getStorageService().getInventory().getAvailableStacks().get(water) == 34,
                        "two-way interface cycle terminates without duplicating advertised resources");
                helper.assertTrue(storage.extract(water, 3, Actionable.MODULATE, source) == 3,
                        "cyclic bridge still extracts available real units once");

                level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
                helper.assertTrue(bus.storage().getAvailableStacks().get(water) == 0,
                        "removed interface is not retained by bridge");

                for (var pos : java.util.List.of(cable, target, drive, cable.west(), drive.east(), returnTarget, returnCable)) {
                    level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                }
                System.out.println("THE_B5_STORAGE_BUS_INTERFACE_PASS");
                helper.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 180)
    public static void containerRouteAndVoidJarOverflow(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var cable = helper.absolutePos(new BlockPos(3, 2, 3));
        var target = cable.south();

        level.setBlockAndUpdate(cable.west(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState());
        level.setBlockAndUpdate(target, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("thaumcraft:warded_jar")).defaultBlockState());

        PartHelper.setPart(level, cable, null, null, IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        var bus = (EssentiaStorageBusPart) PartHelper.setPart(level, cable, Direction.SOUTH, null, ThaumicEnergistics.STORAGE_BUS.get());

        var jar = level.getCapability(EssentiaCapabilities.CONTAINER, target, null);
        jar.setAspects(new AspectList().add(Aspects.WATER, 20));
        var water = EssentiaKey.of(Aspects.WATER);
        var fire = EssentiaKey.of(Aspects.FIRE);
        var source = IActionSource.empty();

        helper.runAfterDelay(50, () -> {
            helper.assertTrue(bus.getMainNode().isActive(), "storage bus has active node");
            var grid = bus.getMainNode().getGrid();
            var storage = grid.getStorageService().getInventory();

            helper.assertTrue(storage.getAvailableStacks().get(water) == 20, "network mounts jar as essentia storage");
            helper.assertTrue(storage.insert(water, 100, Actionable.SIMULATE, source) == 44 && jar.containerContains(Aspects.WATER) == 20,
                    "container simulation bounded by 64-unit jar capacity");
            helper.assertTrue(storage.insert(water, 100, Actionable.MODULATE, source) == 44 && jar.containerContains(Aspects.WATER) == 64,
                    "network inserts exactly available jar room");
            helper.assertTrue(storage.extract(water, 1000, Actionable.SIMULATE, source) == 64 && jar.containerContains(Aspects.WATER) == 64,
                    "extraction simulates actual available amount");
            helper.assertTrue(storage.extract(water, 4, Actionable.MODULATE, source) == 4 && jar.containerContains(Aspects.WATER) == 60,
                    "network extracts from native jar");

            bus.filter(0, fire);
            helper.assertTrue(storage.getAvailableStacks().get(water) == 0 && storage.extract(water, 1, Actionable.MODULATE, source) == 0,
                    "whitelist hides and blocks unlisted aspect");

            var inverter = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:inverter_card"));
            helper.assertTrue(bus.getUpgrades().insertItem(0, new ItemStack(inverter), false).isEmpty(), "inverter upgrade accepted");
            helper.assertTrue(bus.inverted() && storage.getAvailableStacks().get(water) == 0, "original inverter display quirk retained");
            helper.assertTrue(storage.extract(water, 2, Actionable.MODULATE, source) == 2 && jar.containerContains(Aspects.WATER) == 58,
                    "inverted transfer extracts unlisted aspect even when not enumerated");
            helper.assertTrue(storage.insert(fire, 1, Actionable.MODULATE, source) == 0, "blacklisted aspect blocked");

            bus.filter(0, null);
            bus.priority(-123456789);
            bus.allowVoid(true);
            helper.assertTrue(storage.insert(water, 2, Actionable.MODULATE, source) == 2, "empty filter accepts all even with inverter");

            var saved = new CompoundTag();
            bus.writeToNBT(saved, level.registryAccess());
            var restored = ThaumicEnergistics.STORAGE_BUS.get().createPart();
            restored.readFromNBT(saved, level.registryAccess());
            helper.assertTrue(restored.priority() == -123456789 && restored.allowVoid() && restored.inverted(),
                    "native NBT preserves full priority, void, and upgrade state");

            jar.setAspects(new AspectList().add(Aspects.WATER, 8));
            helper.runAfterDelay(30, () -> {
                helper.assertTrue(grid.getStorageService().getCachedInventory().get(water) == 8,
                        "twenty-tick polling updates jar mutation in network cache");

                level.setBlockAndUpdate(target, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("thaumcraft:void_jar")).defaultBlockState());
                var voidJar = level.getCapability(EssentiaCapabilities.CONTAINER, target, null);
                voidJar.setAspects(new AspectList().add(Aspects.WATER, 64));

                bus.allowVoid(false);
                helper.assertTrue(storage.insert(water, 32, Actionable.MODULATE, source) == 0, "default cannot void full jar input");

                bus.allowVoid(true);
                helper.assertTrue(storage.insert(water, 32, Actionable.MODULATE, source) == 32
                        && voidJar.containerContains(Aspects.WATER) == 64,
                        "explicit void mode accepts overflow without phantom storage");

                level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
                helper.assertTrue(storage.extract(water, 1, Actionable.MODULATE, source) == 0 && storage.getAvailableStacks().get(water) == 0,
                        "removed target is never reused through stale container reference");

                level.setBlockAndUpdate(cable, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(cable.west(), Blocks.AIR.defaultBlockState());
                System.out.println("THE_B5_STORAGE_BUS_CONTAINER_PASS");
                helper.succeed();
            });
        });
    }
}
