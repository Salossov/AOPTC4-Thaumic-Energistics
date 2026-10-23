package thaumicenergistics.verification;

import appeng.api.parts.PartHelper;
import appeng.items.tools.NetworkToolItem;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.api.aspects.AspectApi;
import thaumcraft.api.aspects.Aspects;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.StorageBusMenu;
import thaumicenergistics.menu.StoragePriorityMenu;
import thaumicenergistics.network.PriorityPayload;
import thaumicenergistics.storage.EssentiaKey;

@GameTestHolder(ThaumicEnergistics.ID)
@PrefixGameTestTemplate(false)
public final class StorageMenuNativeTest {
    private static ItemStack item(String path, int count) { return new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:"+path)),count); }
    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativeStorageMenuAndTool(GameTestHelper helper) {
        VerificationReports.install(); var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(3,2,3));
        var part = PartHelper.setPart(level,pos,Direction.SOUTH,null,ThaumicEnergistics.STORAGE_BUS.get());
        var player = helper.makeMockPlayer(GameType.SURVIVAL); player.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        player.getInventory().setItem(0,item("network_tool",1)); var tool = player.getInventory().getItem(0);
        var menu = new StorageBusMenu(7,player.getInventory(),part);
        player.containerMenu = menu;
        helper.assertTrue(menu.stillValid(player) && menu.hasNetworkTool() && menu.slots.size() == 55, "native storage menu has nine ghosts, one upgrade, player inventory and tool");
        var water = AspectApi.wispEssence(Aspects.WATER); menu.setCarried(water.copy()); menu.clicked(8,0,ClickType.PICKUP,player);
        helper.assertTrue(part.filter(8).equals("aqua") && ItemStack.matches(menu.getCarried(),water), "all nine filter positions work without capacity cards or cursor consumption");
        menu.clicked(0,0,ClickType.PICKUP,player);
        helper.assertTrue(part.filter(0).isEmpty(), "duplicate ghost filter refused");
        menu.setCarried(new ItemStack(Items.DIAMOND)); menu.clicked(8,0,ClickType.PICKUP,player);
        helper.assertTrue(part.filter(8).isEmpty() && menu.getCarried().is(Items.DIAMOND), "original non-aspect cursor clears filter without consuming item");
        player.getInventory().setItem(9,water.copy()); menu.quickMoveStack(player,10);
        helper.assertTrue(part.filter(0).equals("aqua") && ItemStack.matches(player.getInventory().getItem(9),water), "shift click adds first filter without moving aspect item");
        player.getInventory().setItem(9,item("inverter_card",3)); menu.quickMoveStack(player,10); menu.quickMoveStack(player,10);
        helper.assertTrue(part.inverted() && NetworkToolItem.getInventory(tool).getStackInSlot(0).getCount() == 2, "shift installs one inverter then stores surplus in actual tool");
        player.getInventory().setItem(10,item("speed_card",1)); menu.quickMoveStack(player,11);
        helper.assertTrue(NetworkToolItem.getInventory(tool).getStackInSlot(3).is(item("speed_card",1).getItem()) && NetworkToolItem.getInventory(tool).getStackInSlot(1).isEmpty(),
                "network-tool shift uses original column-before-row slot order");
        helper.assertTrue(!menu.getSlot(37).mayPickup(player), "bound tool locked in its real hotbar slot");
        menu.clicked(46,0,ClickType.SWAP,player);
        helper.assertTrue(player.getInventory().getItem(0) == tool, "hotbar swap cannot move bound tool into itself");
        helper.assertTrue(menu.clickMenuButton(player,StorageBusMenu.VOID) && part.allowVoid() && menu.allowVoid(), "server toggles and syncs void mode");
        helper.assertTrue(!menu.clickMenuButton(player,999), "unknown storage-menu action rejected");
        player.getInventory().setItem(0,tool.copy());
        helper.assertTrue(!menu.stillValid(player) && !menu.clickMenuButton(player,StorageBusMenu.VOID) && part.allowVoid(), "replaced tool invalidates menu and rejects mutation");
        level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        System.out.println("THE_NATIVE_STORAGE_MENU_PASS"); helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void nativePriorityValidationAndCodecs(GameTestHelper helper) {
        VerificationReports.install(); var level = helper.getLevel(); var pos = helper.absolutePos(new BlockPos(3,2,3));
        var part = PartHelper.setPart(level,pos,Direction.SOUTH,null,ThaumicEnergistics.STORAGE_BUS.get());
        var player = helper.makeMockPlayer(GameType.SURVIVAL); player.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
        var previous = player.containerMenu;
        try {
            var menu = new StoragePriorityMenu(23,player.getInventory(),part); player.containerMenu = menu;
            helper.assertTrue(menu.slots.isEmpty() && menu.stillValid(player), "original priority menu has no inventory slots");
            var clientView = new StoragePriorityMenu(23,player.getInventory(),null);
            for (int priority : new int[]{0,1,-1,Integer.MIN_VALUE,Integer.MAX_VALUE,-123456789}) {
                clientView.setData(0,(short)priority); clientView.setData(1,(short)(priority >>> 16));
                helper.assertTrue(clientView.priority() == priority, "vanilla signed-short data packets reconstruct full signed int priority");
                var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(),level.registryAccess());
                try {
                    PriorityPayload.CODEC.encode(buffer,new PriorityPayload(23,priority));
                    var decoded = PriorityPayload.CODEC.decode(buffer);
                    helper.assertTrue(menu.setPriority(player,decoded.containerId(),decoded.value()) && part.priority() == priority && menu.priority() == priority,
                            "priority payload preserves and applies exact signed value on current native menu");
                } finally { buffer.release(); }
            }
            helper.assertTrue(!menu.setPriority(player,24,100) && part.priority() == -123456789, "wrong container ID cannot target current menu");
            player.containerMenu = previous;
            helper.assertTrue(!menu.setPriority(player,23,100), "closed menu rejects absolute-priority payload");
            player.containerMenu = menu;
            part.priority(Integer.MAX_VALUE); menu.clickMenuButton(player,1);
            helper.assertTrue(part.priority() == Integer.MIN_VALUE, "original plus-one signed overflow retained");
            menu.clickMenuButton(player,4); helper.assertTrue(part.priority() == Integer.MAX_VALUE, "original minus-one signed overflow retained");
            part.priority(10000); for (int action = 1; action <= 6; action++) helper.assertTrue(menu.clickMenuButton(player,action), "six original delta actions accepted");
            helper.assertTrue(part.priority() == 10000 && !menu.clickMenuButton(player,7), "six deltas cancel and unknown delta is rejected");
            var other = helper.makeMockPlayer(GameType.SURVIVAL); other.setPos(player.position()); other.containerMenu = menu;
            helper.assertTrue(!menu.setPriority(other,23,0), "different player cannot mutate another owner's priority menu");
            player.setPos(pos.getX()+20,pos.getY(),pos.getZ());
            helper.assertTrue(!menu.setPriority(player,23,0) && !menu.clickMenuButton(player,1), "distant player cannot mutate priority");
            player.setPos(pos.getX()+.5,pos.getY()+.5,pos.getZ()+.5);
            part.getHost().removePartFromSide(Direction.SOUTH); PartHelper.setPart(level,pos,Direction.SOUTH,null,ThaumicEnergistics.STORAGE_BUS.get());
            helper.assertTrue(!menu.setPriority(player,23,0) && !menu.stillValid(player), "replacement at same position cannot reuse stale priority menu");
        } finally { player.containerMenu = previous; level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState()); }
        System.out.println("THE_NATIVE_PRIORITY_MENU_PASS"); helper.succeed();
    }
}
