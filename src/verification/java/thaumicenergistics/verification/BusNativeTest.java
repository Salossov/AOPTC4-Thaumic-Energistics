package thaumicenergistics.verification;

import appeng.api.config.Actionable;
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
import thaumicenergistics.parts.EssentiaIOBusPart;
import thaumicenergistics.storage.CellTier;
import thaumicenergistics.storage.EssentiaCell;
import thaumicenergistics.storage.EssentiaKey;

@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class BusNativeTest {
    @GameTest(template = "empty", timeoutTicks = 220)
    public static void actualCableBusImportAndExport(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var drive = helper.absolutePos(new BlockPos(2, 2, 2));
        var cable = drive.south();
        var jar = cable.south();
        level.setBlockAndUpdate(drive, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:drive")).defaultBlockState());
        level.setBlockAndUpdate(drive.east(), BuiltInRegistries.BLOCK.get(ResourceLocation.parse("ae2:creative_energy_cell")).defaultBlockState());
        level.setBlockAndUpdate(jar, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("thaumcraft:warded_jar")).defaultBlockState());
        var container = level.getCapability(EssentiaCapabilities.CONTAINER, jar, null);
        container.setAspects(new AspectList().add(Aspects.WATER, 64));
        var stack = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());
        new EssentiaCell(stack, null).insert(EssentiaKey.of(Aspects.EARTH), 2047, Actionable.MODULATE, IActionSource.empty());
        var driveSlots = level.getCapability(Capabilities.ItemHandler.BLOCK, drive, Direction.UP);
        helper.assertTrue(driveSlots.insertItem(0, stack, false).isEmpty(), "cell inserted in live drive");
        var cableItem = IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable"));
        helper.assertTrue(cableItem != null, "actual AE2 glass cable item exists");
        PartHelper.setPart(level, cable, null, null, cableItem);
        var importing = PartHelper.setPart(level, cable, Direction.SOUTH, null, ThaumicEnergistics.IMPORT_BUS.get());
        helper.assertTrue(importing != null && importing.importing(), "custom import part placed in actual cable host");
        helper.assertTrue(EssentiaIOBusPart.transferAmount(0,10) == 2 && EssentiaIOBusPart.transferAmount(4,40) == 64, "original transfer rate and clamp");
        helper.runAfterDelay(70, () -> {
            helper.assertTrue(importing.getMainNode().isActive(), "import part booted and has power/channel");
            helper.assertTrue(container.containerContains(Aspects.WATER) == 63, "ticked import consumes one accepted unit instead of full request");
            var cell = new EssentiaCell(driveSlots.getStackInSlot(0), null);
            helper.assertTrue(cell.ledger().total() == 2048 && cell.ledger().contents().amounts().get("aqua") == 1, "actual scheduled import conserves units");
            var host = PartHelper.getPartHost(level, cable);
            host.removePartFromSide(Direction.SOUTH);
            var exporting = PartHelper.setPart(level, cable, Direction.SOUTH, null, ThaumicEnergistics.EXPORT_BUS.get());
            exporting.filter(4, EssentiaKey.of(Aspects.WATER));
            var capacity = BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:capacity_card"));
            helper.assertTrue(exporting.getUpgrades().insertItem(0, new ItemStack(capacity), false).isEmpty(), "capacity card accepted by registered upgrade inventory");
            helper.assertTrue(exporting.availableFilterSlots().length == 5, "first capacity card exposes five legacy slots");
            exporting.filter(1, EssentiaKey.of(Aspects.FIRE));
            var saved = new CompoundTag(); exporting.writeToNBT(saved, level.registryAccess());
            var restored = ThaumicEnergistics.EXPORT_BUS.get().createPart(); restored.readFromNBT(saved, level.registryAccess());
            helper.assertTrue(restored.filter(4).equals("aqua") && restored.filter(1).equals("ignis") && restored.availableFilterSlots().length == 5,
                    "part filters and real upgrade inventory survive native NBT path");
            exporting.getUpgrades().extractItem(0,1,false);
            helper.assertTrue(exporting.filter(1).isEmpty() && exporting.availableFilterSlots().length == 1, "removing capacity card clears disabled filters as original");
            helper.runAfterDelay(60, () -> {
                helper.assertTrue(exporting.getMainNode().isActive(), "export part active after replacement");
                helper.assertTrue(container.containerContains(Aspects.WATER) == 64, "ticked export fills only one available jar unit");
                helper.assertTrue(new EssentiaCell(driveSlots.getStackInSlot(0), null).ledger().total() == 2047, "scheduled export preserves remaining contents");
                level.setBlockAndUpdate(cable, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(jar, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(drive, Blocks.AIR.defaultBlockState());
                level.setBlockAndUpdate(drive.east(), Blocks.AIR.defaultBlockState());
                System.out.println("THE_NATIVE_IO_BUSES_PASS");
                helper.succeed();
            });
        });
    }
}
