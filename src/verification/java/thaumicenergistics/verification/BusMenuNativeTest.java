package thaumicenergistics.verification;

import appeng.api.parts.PartHelper;
import appeng.api.stacks.GenericStack;
import appeng.items.tools.NetworkToolItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.AspectApi;
import thaumcraft.api.aspects.Aspects;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.EssentiaBusMenu;
import thaumicenergistics.parts.EssentiaIOBusPart;
import thaumicenergistics.storage.EssentiaKey;

@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class BusMenuNativeTest {
    private static ItemStack item(String id, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:" + id)), count);
    }
    private static EssentiaIOBusPart place(GameTestHelper helper, BlockPos pos) {
        return PartHelper.setPart(helper.getLevel(), pos, Direction.SOUTH, null, ThaumicEnergistics.EXPORT_BUS.get());
    }
    private static ItemStack phial(thaumcraft.api.aspects.Aspect aspect) {
        return AspectApi.variants().stream()
                .filter(stack -> BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(ResourceLocation.parse("thaumcraft:essentia_phial")))
                .filter(stack -> EssentiaKey.of(aspect).equals(EssentiaBusMenu.aspectFromItem(stack)))
                .findFirst().orElseThrow();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeFilterItemInspection(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3,2,3));
        var part = place(helper, pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + 1.5);
        var menu = new EssentiaBusMenu(1, player.getInventory(), part);
        var wisp = AspectApi.wispEssence(Aspects.WATER);
        helper.assertTrue(wisp.getItem() instanceof thaumcraft.api.aspects.IAspectItem readable
                && readable.getAspects(wisp).getAmount(Aspects.WATER) == 2, "native wisp exposes original two units through approved read-only interface");
        helper.assertTrue(!(wisp.getItem() instanceof thaumcraft.api.aspects.IEssentiaContainerItem), "read-only inspection does not opt wisp into processing bonuses");
        var original = wisp.copy();
        menu.setCarried(wisp);
        menu.clicked(4, 0, ClickType.PICKUP, player);
        helper.assertTrue(part.filter(4).equals("aqua") && ItemStack.matches(menu.getCarried(), original), "wisp configures filter without changing its data or count");
        var label = BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:jar_label")).getDefaultInstance();
        helper.assertTrue(label.getItem() instanceof thaumcraft.api.aspects.IAspectItem && EssentiaBusMenu.aspectFromItem(label) == null,
                "unset native label exposes no selected aspect");
        var tag = new net.minecraft.nbt.CompoundTag(); tag.putString("Aspect", "terra");
        label.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        label.setCount(5);
        menu.setCarried(label);
        menu.clicked(4, 0, ClickType.PICKUP, player);
        helper.assertTrue(part.filter(4).equals("terra") && menu.getCarried().getCount() == 5, "configured native label reads preserved component format without consumption");
        helper.assertTrue(!(label.getItem() instanceof thaumcraft.api.aspects.IEssentiaContainerItem), "label remains outside contained-essentia processing bonus marker");
        var jar = BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:warded_jar")).getDefaultInstance();
        ((thaumcraft.api.aspects.IEssentiaJarItem) jar.getItem()).setAspects(jar, new thaumcraft.api.aspects.AspectList().add(Aspects.FIRE, 12));
        helper.assertTrue(EssentiaKey.of(Aspects.FIRE).equals(EssentiaBusMenu.aspectFromItem(jar)), "filled jar supplies real contained aspect");
        helper.assertTrue(EssentiaBusMenu.aspectFromItem(new ItemStack(Items.DIAMOND)) == null, "ordinary item object aspects cannot configure essentia filter");
        var importing = PartHelper.setPart(level, pos, Direction.NORTH, null, ThaumicEnergistics.IMPORT_BUS.get());
        helper.assertTrue(!new EssentiaBusMenu(2, player.getInventory(), importing).clickMenuButton(player, EssentiaBusMenu.VOID_BUTTON), "import menu rejects export-only void action");
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        System.out.println("THE_NATIVE_FILTER_ITEMS_PASS");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeBusMenuActions(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3,2,3));
        var part = place(helper, pos);
        // Menu actions run on the native server without a client/login handshake.
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        try {
            player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + 1.5);
            var menu = new EssentiaBusMenu(1, player.getInventory(), part);
            helper.assertTrue(menu.getType() == ThaumicEnergistics.BUS_MENU.get(), "native menu uses registered type");
            helper.assertTrue(menu.stillValid(player) && menu.slots.size() == 49, "live menu without tool has 9 ghosts, 4 upgrades, 36 player slots");
            var essence = phial(Aspects.WATER);
            helper.assertTrue(!essence.isEmpty(), "public core API supplies native contained-aspect stack");
            menu.setCarried(essence.copy());
            menu.clicked(4, 0, ClickType.PICKUP, player);
            helper.assertTrue(part.filter(4).equals("aqua") && ItemStack.matches(menu.getCarried(), essence), "ghost filter reads server cursor without consuming contents");
            helper.assertTrue(GenericStack.unwrapItemStack(menu.getSlot(4).getItem()).what().equals(EssentiaKey.of(Aspects.WATER)), "ghost aspect uses registered native AE key component for slot synchronization");
            menu.clicked(0, 0, ClickType.PICKUP, player);
            helper.assertTrue(part.filter(0).isEmpty(), "disabled ghost rejects forged slot click");
            helper.assertTrue(!menu.clickMenuButton(player, EssentiaBusMenu.REDSTONE_BUTTON), "redstone action rejected without card");
            helper.assertTrue(!menu.clickMenuButton(player, 65535), "unknown action rejected");
            menu.setCarried(ItemStack.EMPTY);
            menu.clicked(4, 0, ClickType.PICKUP, player);
            helper.assertTrue(part.filter(4).isEmpty(), "empty cursor clears filter");
            player.getInventory().setItem(9, essence.copy());
            menu.quickMoveStack(player, 13);
            helper.assertTrue(part.filter(4).equals("aqua") && ItemStack.matches(player.getInventory().getItem(9), essence), "shift click configures center without moving the original item");
            player.getInventory().setItem(10, item("capacity_card", 3));
            menu.quickMoveStack(player, 14);
            helper.assertTrue(part.capacityCards() == 2 && player.getInventory().getItem(10).getCount() == 1, "menu respects two-capacity-card limit during multi-slot transfer");
            player.getInventory().setItem(9, phial(Aspects.FIRE));
            menu.quickMoveStack(player, 13);
            helper.assertTrue(part.filter(1).equals("ignis"), "shift filter uses original 4,1,3,5,7 ordering");
            menu.quickMoveStack(player, 13);
            helper.assertTrue(part.filter(3).isEmpty(), "duplicate shift aspect does not occupy another filter");
            player.getInventory().setItem(11, item("redstone_card", 1));
            menu.quickMoveStack(player, 15);
            helper.assertTrue(menu.hasRedstoneCard() && menu.clickMenuButton(player, EssentiaBusMenu.REDSTONE_BUTTON), "installed card enables mode action");
            var mode = part.redstoneMode();
            helper.assertTrue(menu.clickMenuButton(player, EssentiaBusMenu.VOID_BUTTON) && part.allowVoid(), "export void toggle updates server part");
            menu.clicked(9, 0, ClickType.PICKUP, player);
            menu.setCarried(ItemStack.EMPTY);
            menu.clicked(10, 0, ClickType.PICKUP, player);
            menu.broadcastChanges();
            helper.assertTrue(part.capacityCards() == 0 && part.filter(1).isEmpty() && !menu.filterEnabled(1), "removing actual menu upgrades clears hidden filters");
            player.setPos(pos.getX() + 20, pos.getY(), pos.getZ());
            helper.assertTrue(!menu.stillValid(player) && !menu.clickMenuButton(player, EssentiaBusMenu.REDSTONE_BUTTON), "distant player cannot change mode");
            player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + 1.5);
            part.getHost().removePartFromSide(Direction.SOUTH);
            place(helper, pos);
            helper.assertTrue(!menu.stillValid(player) && !menu.clickMenuButton(player, EssentiaBusMenu.REDSTONE_BUTTON) && part.redstoneMode() == mode,
                    "replaced part cannot be changed through stale menu");
            System.out.println("THE_NATIVE_BUS_MENU_PASS");
        } finally {
            level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeNetworkToolPanel(GameTestHelper helper) {
        VerificationReports.install();
        var level = helper.getLevel();
        var pos = helper.absolutePos(new BlockPos(3,2,3));
        var part = place(helper, pos);
        var player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setPos(pos.getX() + .5, pos.getY() + .5, pos.getZ() + 1.5);
        player.getInventory().setItem(0, item("network_tool", 1));
        var tool = player.getInventory().getItem(0);
        var menu = new EssentiaBusMenu(1, player.getInventory(), part);
        helper.assertTrue(menu.hasNetworkTool() && menu.slots.size() == 58 && menu.stillValid(player), "actual network tool adds nine slots");
        helper.assertTrue(!menu.getSlot(40).mayPickup(player), "bound tool slot locked against pickup");
        player.getInventory().setItem(9, item("speed_card", 6));
        menu.quickMoveStack(player, 13);
        menu.quickMoveStack(player, 13);
        helper.assertTrue(part.speedCards() == 4 && player.getInventory().getItem(9).isEmpty(), "shift installs four speed cards then stores remainder in tool");
        helper.assertTrue(NetworkToolItem.getInventory(tool).getStackInSlot(0).getCount() == 2, "tool component receives remaining real cards");
        var saved = tool.save(level.registryAccess());
        var restored = ItemStack.parse(level.registryAccess(), saved).orElseThrow();
        helper.assertTrue(NetworkToolItem.getInventory(restored).getStackInSlot(0).getCount() == 2, "tool cards survive native stack serialization");
        menu.clicked(49, 0, ClickType.SWAP, player);
        helper.assertTrue(player.getInventory().getItem(0) == tool, "hotbar swap cannot move bound tool into itself");
        menu.clicked(40, 0, ClickType.THROW, player);
        helper.assertTrue(player.getInventory().getItem(0) == tool, "throw cannot remove bound tool");
        menu.setCarried(new ItemStack(Items.DIAMOND));
        menu.clicked(49, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().is(Items.DIAMOND) && NetworkToolItem.getInventory(tool).getStackInSlot(0).getCount() == 2, "tool inventory refuses ordinary items");
        player.getInventory().setItem(0, tool.copy());
        helper.assertTrue(!menu.stillValid(player), "replacing tool instance invalidates existing panel");
        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        System.out.println("THE_NATIVE_NETWORK_TOOL_MENU_PASS");
        helper.succeed();
    }
}
