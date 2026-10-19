package thaumicenergistics.verification;

import appeng.api.parts.IPartItem;
import appeng.api.parts.PartHelper;
import com.google.gson.GsonBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import thaumcraft.api.aspects.AspectApi;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.Aspects;
import thaumcraft.api.aspects.EssentiaCapabilities;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.client.CellWorkbenchScreen;
import thaumicenergistics.client.EssentiaBusScreen;
import thaumicenergistics.client.StorageBusScreen;
import thaumicenergistics.client.StoragePriorityScreen;
import thaumicenergistics.menu.CellWorkbenchMenu;
import thaumicenergistics.menu.EssentiaBusMenu;
import thaumicenergistics.menu.StorageBusMenu;
import thaumicenergistics.menu.StoragePriorityMenu;
import thaumicenergistics.parts.EssentiaIOBusPart;
import thaumicenergistics.parts.EssentiaStorageBusPart;
import thaumicenergistics.storage.CellTier;
import thaumicenergistics.storage.EssentiaCell;
import thaumicenergistics.storage.EssentiaKey;

/** Verification source set only: real client screens, packets and framebuffer captures.
 * Uses no desktop input and mutates only a marked, isolated verification save. */
@EventBusSubscriber(modid = ThaumicEnergistics.ID, value = Dist.CLIENT)
public final class ClientMenuSmoke {
    private static final String SAVE = "THE-client-menu-smoke";
    private static final BlockPos CABLE = new BlockPos(3,100,3), BENCH = new BlockPos(5,100,3);
    private static final BlockPos VIS_INPUT = new BlockPos(3,100,12), VIS_OUTPUT = new BlockPos(3,100,14);
    private static final AtomicReference<Throwable> SERVER_ERROR = new AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicBoolean VIS_LINK_DRAINED = new java.util.concurrent.atomic.AtomicBoolean();
    private static final Map<String,Object> EVIDENCE = new LinkedHashMap<>();
    private static final List<String> CAPTURES = new ArrayList<>();
    private static final long RUN = System.currentTimeMillis();
    private static int step, age, totalTicks;
    private static boolean queued, finished;
    private static String requestedCapture;
    private static Path awaitingCapture;

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("thaumicenergistics.clientSmoke") || finished) return;
        var mc = Minecraft.getInstance();
        try {
            long window=mc.getWindow().getWindow();
            if (!Boolean.getBoolean("thaumicenergistics.hiddenWindowHook") || !Boolean.getBoolean("thaumicenergistics.silentSoundHook"))
                throw new IllegalStateException("Hidden/silent verification hooks were not installed");
            if (org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window,org.lwjgl.glfw.GLFW.GLFW_VISIBLE)!=0
                    || org.lwjgl.glfw.GLFW.glfwGetWindowAttrib(window,org.lwjgl.glfw.GLFW.GLFW_FOCUSED)!=0)
                throw new IllegalStateException("Verification window must stay hidden and unfocused");
            EVIDENCE.put("hidden_unfocused_window",true); EVIDENCE.put("sound_playback_blocked",true);
            if (++totalTicks > 6000) throw new IllegalStateException("Client smoke timeout; screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getName()));
            if (totalTicks % 200 == 0) System.out.println("THE_CLIENT_SMOKE_STATE step=" + step + " screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getName()));
            if (SERVER_ERROR.get() != null) throw new IllegalStateException("Server fixture/action failed",SERVER_ERROR.get());
            if (awaitingCapture != null) {
                if (Files.isRegularFile(awaitingCapture) && Files.size(awaitingCapture) > 100) {
                    CAPTURES.add(awaitingCapture.toAbsolutePath().toString()); awaitingCapture = null; advance();
                }
                return;
            }
            if (mc.level == null || mc.player == null || mc.gameMode == null) {
                if (mc.screen instanceof net.minecraft.client.gui.screens.BackupConfirmScreen
                        && Files.isRegularFile(mc.gameDirectory.toPath().resolve("saves").resolve(SAVE).resolve(".thaumicenergistics-smoke-owned"))) {
                    for (var child : mc.screen.children()) if (child instanceof Button button && button.active
                            && button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents
                            && contents.getKey().equals("selectWorld.backupJoinConfirmButton")) {
                        EVIDENCE.put("owned_test_save_backup_confirmed",true); button.onPress(); break;
                    }
                }
                // Only the isolated quick-play launch enables this harness. Accept the
                // vanilla experimental-settings confirmation for this newly made test save.
                if (mc.screen != null && mc.screen.getClass().getSimpleName().contains("ConfirmExperimental")) {
                    for (var child : mc.screen.children()) if (child instanceof Button button && button.active
                            && (button.getMessage().getString().equals("Proceed") || button.getMessage().getString().equals("I know what I'm doing!"))) {
                        button.onPress(); break;
                    }
                }
                return;
            }
            if (++age < 12) return;
            switch (step) {
                case 0 -> {
                    if (!queued) { queued = true; server(ClientMenuSmoke::fixture); }
                }
                case 1 -> {
                    if (mc.screen instanceof StorageBusScreen && mc.player.containerMenu instanceof StorageBusMenu menu && menu.hasNetworkTool()) capture("storage-main");
                }
                case 2 -> { click(10,ClickType.PICKUP); advance(); }
                case 3 -> {
                    if (!mc.player.containerMenu.getCarried().isEmpty()) { click(4,ClickType.PICKUP); advance(); }
                }
                case 4 -> {
                    if (mc.player.containerMenu instanceof StorageBusMenu menu && EssentiaKey.of(Aspects.WATER).equals(menu.filter(4))) {
                        EVIDENCE.put("server_cursor_filter_packet",true); capture("storage-filter");
                    }
                }
                case 5 -> { click(10,ClickType.PICKUP); click(11,ClickType.QUICK_MOVE); advance(); }
                case 6 -> {
                    if (mc.player.containerMenu.getSlot(9).getItem().is(ae("inverter_card").getItem())) {
                        click(11,ClickType.QUICK_MOVE); button(StorageBusMenu.VOID); advance();
                    }
                }
                case 7 -> {
                    if (mc.player.containerMenu instanceof StorageBusMenu menu && menu.allowVoid() && !menu.getSlot(46).getItem().isEmpty()) {
                        if(!EssentiaKey.of(Aspects.WATER).equals(menu.filter(4))) throw new IllegalStateException("Storage filter lost after inverter insertion");
                        EVIDENCE.put("storage_filter_retained_after_inverter",true);
                        EVIDENCE.put("inverter_tool_and_void_packets",true); capture("storage-inverter-void");
                    }
                }
                case 8 -> { button(StorageBusMenu.PRIORITY); advance(); }
                case 9 -> { if (mc.screen instanceof StoragePriorityScreen) { button(3); advance(); } }
                case 10 -> {
                    if (mc.player.containerMenu instanceof StoragePriorityMenu menu && menu.priority() == 100) capture("priority-delta");
                }
                case 11 -> {
                    var field = editBox(); field.setValue("");
                    for (char c : "-1234567".toCharArray()) field.charTyped(c,0);
                    advance();
                }
                case 12 -> {
                    if (mc.player.containerMenu instanceof StoragePriorityMenu menu && menu.priority() == -1234567 && editBox().getValue().equals("-1234567")) {
                        EVIDENCE.put("absolute_priority_real_payload",true); capture("priority-negative");
                    }
                }
                case 13 -> { editBox().keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_BACKSPACE,0,0); advance(); }
                case 14 -> {
                    if (mc.player.containerMenu instanceof StoragePriorityMenu menu && menu.priority() == -123456) {
                        EVIDENCE.put("backspace_at_typing_limit",true); button(0); advance();
                    }
                }
                case 15 -> {
                    if (mc.screen instanceof StorageBusScreen) { server(player -> ((EssentiaIOBusPart)PartHelper.getPartHost(player.level(),CABLE).getPart(Direction.EAST)).onUseWithoutItem(player,Vec3.atCenterOf(CABLE))); advance(); }
                }
                case 16 -> { if (mc.screen instanceof EssentiaBusScreen && mc.player.containerMenu instanceof EssentiaBusMenu menu && menu.importing()) { click(15,ClickType.QUICK_MOVE); advance(); } }
                case 17 -> {
                    if (mc.player.containerMenu instanceof EssentiaBusMenu menu && menu.filterEnabled(0)) {
                        EVIDENCE.put("io_capacity_card_packet",true); capture("import-capacity");
                    }
                }
                case 18 -> { server(player -> ((EssentiaIOBusPart)PartHelper.getPartHost(player.level(),CABLE).getPart(Direction.WEST)).onUseWithoutItem(player,Vec3.atCenterOf(CABLE))); advance(); }
                case 19 -> {
                    if (mc.screen instanceof EssentiaBusScreen && mc.player.containerMenu instanceof EssentiaBusMenu menu && !menu.importing()) capture("export-menu");
                }
                case 20 -> {
                    server(player -> player.level().getBlockState(BENCH).useWithoutItem(player.level(),player,new BlockHitResult(Vec3.atCenterOf(BENCH),Direction.UP,BENCH,false)));
                    advance();
                }
                case 21 -> { if (mc.screen instanceof CellWorkbenchScreen) { click(67,ClickType.QUICK_MOVE); advance(); } }
                case 22 -> {
                    if (mc.player.containerMenu instanceof CellWorkbenchMenu menu && !menu.getSlot(CellWorkbenchMenu.CELL_SLOT).getItem().isEmpty()) {
                        button(CellWorkbenchMenu.PARTITION_CONTENTS); advance();
                    }
                }
                case 23 -> {
                    if (mc.player.containerMenu instanceof CellWorkbenchMenu menu && menu.filter(0) != null && menu.filter(1) != null) {
                        EVIDENCE.put("workbench_real_cell_and_partition_packets",true); capture("workbench-partitions");
                    }
                }
                case 24 -> { mc.player.closeContainer(); advance(); }
                case 25 -> { if (mc.screen == null) capture("world-fixture"); }
                case 26 -> { server(ClientMenuSmoke::visFixture); advance(); }
                case 27 -> {
                    server(player -> {
                        var host=PartHelper.getPartHost(player.level(),VIS_OUTPUT);
                        if(host!=null && host.getPart(Direction.SOUTH) instanceof thaumicenergistics.parts.VisInterfacePart part && part.isActive()) {
                            var input=(thaumicenergistics.parts.VisInterfacePart)PartHelper.getPartHost(player.level(),VIS_INPUT).getPart(Direction.WEST);
                            if(!input.isActive()) return;
                            if(!part.isVisProvider()) {
                                var card=ae("memory_card"); player.setShiftKeyDown(true); input.onUseItemOn(card,player,InteractionHand.MAIN_HAND,Vec3.ZERO);
                                player.setShiftKeyDown(false); part.onUseItemOn(card,player,InteractionHand.MAIN_HAND,Vec3.ZERO);
                                if(!part.isVisProvider()) throw new IllegalStateException("Actual Vis memory-card link failed after grid boot");
                            }
                            if(part.consumeVis(Aspects.WATER,1)>0) VIS_LINK_DRAINED.set(true);
                        }
                    });
                    var host=PartHelper.getPartHost(mc.level,VIS_OUTPUT);
                    if(VIS_LINK_DRAINED.get() && host!=null && host.getPart(Direction.SOUTH) instanceof thaumicenergistics.parts.VisInterfacePart part && part.drainColor()==Aspects.WATER.getColor()) {
                        EVIDENCE.put("vis_drain_color_real_client_sync",true); capture("vis-interface-water");
                    }
                }
                case 28 -> { mc.setScreen(new AspectPaletteScreen()); advance(); }
                case 29 -> {
                    if(mc.screen instanceof AspectPaletteScreen palette) {
                        EVIDENCE.put("aspect_palette",palette.samples()); EVIDENCE.put("gui_scale",mc.getWindow().getGuiScale());
                        EVIDENCE.put("aspect_palette_rendered",true); capture("aspect-palette");
                    }
                }
                case 30 -> finish(true,null);
                default -> throw new IllegalStateException("Unknown smoke step " + step);
            }
            if (age > 600) throw new IllegalStateException("Client smoke step timeout " + step + "; screen=" + (mc.screen == null ? "none" : mc.screen.getClass().getName()));
        } catch (Throwable failure) { finish(false,failure); }
    }
    private static ItemStack ae(String path) { return BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:"+path)).getDefaultInstance(); }
    private static void fixture(ServerPlayer player) {
        var level = player.serverLevel();
        var root = level.getServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        if (!root.getFileName().toString().equals(SAVE) || !Files.isRegularFile(root.resolve(".thaumicenergistics-smoke-owned")))
            throw new IllegalStateException("Refusing to mutate an unmarked client save: " + root);
        for (int x = 0; x < 9; x++) for (int z = 0; z < 9; z++) level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.SMOOTH_STONE.defaultBlockState());
        level.setDayTime(6000); level.setWeatherParameters(20000,0,false,false);
        var drive = CABLE.north();
        level.setBlockAndUpdate(CABLE,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(drive,block("ae2:drive")); level.setBlockAndUpdate(drive.west(),block("ae2:creative_energy_cell"));
        for (var pos : List.of(CABLE.south(),CABLE.east(),CABLE.west())) {
            level.setBlockAndUpdate(pos,block("thaumcraft:warded_jar"));
            level.getCapability(EssentiaCapabilities.CONTAINER,pos,null).setAspects(new AspectList().add(Aspects.WATER,32));
        }
        PartHelper.setPart(level,CABLE,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        var storage = PartHelper.setPart(level,CABLE,Direction.SOUTH,null,ThaumicEnergistics.STORAGE_BUS.get());
        PartHelper.setPart(level,CABLE,Direction.EAST,null,ThaumicEnergistics.IMPORT_BUS.get());
        var exporting = PartHelper.setPart(level,CABLE,Direction.WEST,null,ThaumicEnergistics.EXPORT_BUS.get()); exporting.filter(4,EssentiaKey.of(Aspects.WATER));
        level.setBlockAndUpdate(BENCH,ThaumicEnergistics.CELL_WORKBENCH.get().defaultBlockState());
        var cell = new ItemStack(ThaumicEnergistics.CELLS.get(CellTier.K1).get());
        var contents = new EssentiaCell(cell,null); contents.insert(EssentiaKey.of(Aspects.WATER),20,appeng.api.config.Actionable.MODULATE,appeng.api.networking.security.IActionSource.empty());
        contents.insert(EssentiaKey.of(Aspects.EARTH),5,appeng.api.config.Actionable.MODULATE,appeng.api.networking.security.IActionSource.empty());
        level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,drive,Direction.UP).insertItem(0,cell.copy(),false);
        player.getInventory().clearContent(); player.setGameMode(GameType.CREATIVE);
        player.getInventory().setItem(0,ae("network_tool")); player.getInventory().selected = 8;
        player.getInventory().setItem(9,AspectApi.wispEssence(Aspects.WATER));
        var inverter = ae("inverter_card"); inverter.setCount(2); player.getInventory().setItem(10,inverter);
        var capacity = ae("capacity_card"); capacity.setCount(2); player.getInventory().setItem(11,capacity);
        player.getInventory().setItem(12,cell);
        player.getInventory().setItem(13,new ItemStack(ThaumicEnergistics.DIFFUSION_CORE.get()));
        player.getInventory().setItem(14,new ItemStack(ThaumicEnergistics.COALESCENCE_CORE.get()));
        player.teleportTo(level,4.5,100,6.5,160,25);
        storage.openMenu(player,false);
        Minecraft.getInstance().execute(ClientMenuSmoke::advance);
    }
    private static void visFixture(ServerPlayer player) {
        var level=player.serverLevel();
        for(int x=0;x<9;x++)for(int z=10;z<18;z++)level.setBlockAndUpdate(new BlockPos(x,99,z),Blocks.SMOOTH_STONE.defaultBlockState());
        for(var pos:List.of(VIS_INPUT,VIS_OUTPUT,VIS_INPUT.south()))level.setBlockAndUpdate(pos,Blocks.AIR.defaultBlockState());
        level.setBlockAndUpdate(VIS_INPUT.east(),block("ae2:creative_energy_cell"));
        PartHelper.setPart(level,VIS_INPUT,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        PartHelper.setPart(level,VIS_INPUT.south(),null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        PartHelper.setPart(level,VIS_OUTPUT,null,null,IPartItem.byId(ResourceLocation.parse("ae2:fluix_glass_cable")));
        var input=PartHelper.setPart(level,VIS_INPUT,Direction.WEST,null,ThaumicEnergistics.VIS_INTERFACE.get());
        var output=PartHelper.setPart(level,VIS_OUTPUT,Direction.SOUTH,null,ThaumicEnergistics.VIS_INTERFACE.get());
        level.setBlockAndUpdate(VIS_INPUT.west(),block("thaumcraft:vis_relay").setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.WEST));
        level.setBlockAndUpdate(VIS_INPUT.west(2),block("thaumcraft:energized_aura_node"));
        level.setBlockAndUpdate(VIS_OUTPUT.south(),block("thaumcraft:vis_relay").setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.SOUTH));
        player.teleportTo(level,4.7,100.1,15.4,112,43);
    }
    private static net.minecraft.world.level.block.state.BlockState block(String id) { return BuiltInRegistries.BLOCK.get(ResourceLocation.parse(id)).defaultBlockState(); }
    private static void server(Consumer<ServerPlayer> action) {
        var mc = Minecraft.getInstance(); var server = mc.getSingleplayerServer(); var id = mc.player.getUUID();
        if (server == null) throw new IllegalStateException("Smoke requires the isolated integrated server");
        server.execute(() -> { try { var player = server.getPlayerList().getPlayer(id); if (player == null) throw new IllegalStateException("Local player missing"); action.accept(player); } catch (Throwable error) { SERVER_ERROR.set(error); } });
    }
    private static EditBox editBox() {
        return Minecraft.getInstance().screen.children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow();
    }
    private static void click(int slot, ClickType type) { var mc = Minecraft.getInstance(); mc.gameMode.handleInventoryMouseClick(mc.player.containerMenu.containerId,slot,0,type,mc.player); }
    private static void button(int id) { var mc = Minecraft.getInstance(); mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId,id); }
    private static void advance() { step++; age = 0; System.out.println("THE_CLIENT_SMOKE_STEP " + step); }
    private static void capture(String name) {
        if (requestedCapture == null && awaitingCapture == null) requestedCapture = "the-"+RUN+"-"+name+".png";
    }
    @SubscribeEvent public static void frame(RenderFrameEvent.Post event) {
        if (!Boolean.getBoolean("thaumicenergistics.clientSmoke") || requestedCapture == null || finished) return;
        var mc = Minecraft.getInstance(); String name = requestedCapture; requestedCapture = null;
        awaitingCapture = mc.gameDirectory.toPath().resolve("screenshots").resolve(name);
        Screenshot.grab(mc.gameDirectory,name,mc.getMainRenderTarget(),message -> System.out.println("THE_CLIENT_SMOKE_SCREENSHOT " + message.getString()));
    }
    private static void finish(boolean passed, Throwable failure) {
        finished = true; var mc = Minecraft.getInstance();
        EVIDENCE.put("automation_passed",passed); EVIDENCE.put("visual_review_pending",true); EVIDENCE.put("step",step); EVIDENCE.put("screenshots",CAPTURES);
        if (failure != null) { EVIDENCE.put("failure",failure.toString()); failure.printStackTrace(); }
        try {
            var output = mc.gameDirectory.toPath().resolve("thaumicenergistics-smoke/result.json"); Files.createDirectories(output.getParent());
            Files.writeString(output,new GsonBuilder().setPrettyPrinting().create().toJson(EVIDENCE));
            System.out.println("THE_CLIENT_SMOKE_" + (passed ? "PASS" : "FAIL") + " " + output.toAbsolutePath());
        } catch (Exception error) { error.printStackTrace(); }
        mc.stop();
    }
}
