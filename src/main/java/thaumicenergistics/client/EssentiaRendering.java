package thaumicenergistics.client;

import appeng.api.client.AEKeyRenderHandler;
import appeng.api.client.AEKeyRendering;
import appeng.api.client.StorageCellModels;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.storage.EssentiaKey;
import thaumicenergistics.storage.EssentiaKeyType;

@EventBusSubscriber(modid = ThaumicEnergistics.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class EssentiaRendering implements AEKeyRenderHandler<EssentiaKey> {
    @SubscribeEvent
    public static void itemColors(net.neoforged.neoforge.client.event.RegisterColorHandlersEvent.Item event) {
        event.register((stack,tint)->switch(tint){
            case 1 -> appeng.api.util.AEColor.TRANSPARENT.blackVariant;
            case 2 -> appeng.api.util.AEColor.TRANSPARENT.mediumVariant;
            case 3 -> appeng.api.util.AEColor.TRANSPARENT.whiteVariant;
            default -> 0xffffff;
        },ThaumicEnergistics.STORAGE_MONITOR.get(),ThaumicEnergistics.CONVERSION_MONITOR.get());
    }
    @SubscribeEvent
    public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(ThaumicEnergistics.BUS_MENU.get(), EssentiaBusScreen::new);
        event.register(ThaumicEnergistics.WORKBENCH_MENU.get(), CellWorkbenchScreen::new);
        event.register(ThaumicEnergistics.STORAGE_BUS_MENU.get(),StorageBusScreen::new);
        event.register(ThaumicEnergistics.PRIORITY_MENU.get(),StoragePriorityScreen::new);
        event.register(ThaumicEnergistics.EMITTER_MENU.get(),EssentiaLevelEmitterScreen::new);
    }
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            AEKeyRendering.register(EssentiaKeyType.INSTANCE, EssentiaKey.class, new EssentiaRendering());
            ThaumicEnergistics.CELLS.values().forEach(cell -> StorageCellModels.registerModel(cell.get(), StorageCellModels.getDefaultModel()));
        });
    }
    private ResourceLocation texture(EssentiaKey key) {
        return key.aspect() == null ? ResourceLocation.withDefaultNamespace("textures/misc/unknown_server.png")
                : ResourceLocation.parse(key.aspect().texture());
    }
    @Override public void drawInGui(Minecraft minecraft, GuiGraphics graphics, int x, int y, EssentiaKey key) {
        int color = key.aspect() == null ? 0xffffff : key.aspect().getColor();
        float[] previous = com.mojang.blaze3d.systems.RenderSystem.getShaderColor().clone();
        graphics.flush();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        graphics.setColor(((color >> 16) & 255) / 255f, ((color >> 8) & 255) / 255f, (color & 255) / 255f, 1);
        // flush() enables depth testing even when the container disabled it for slots.
        com.mojang.blaze3d.systems.RenderSystem.disableDepthTest();
        graphics.blit(texture(key), x, y, 16, 16, 0, 0, 32, 32, 32, 32);
        graphics.flush();
        graphics.setColor(previous[0], previous[1], previous[2], previous[3]);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
    }
    @Override public void drawOnBlockFace(PoseStack poses, MultiBufferSource buffers, EssentiaKey key, float scale, int light, Level level) {
        var vertices = buffers.getBuffer(RenderType.entityTranslucent(texture(key)));
        var pose = poses.last();
        float half = scale / 2;
        int color = key.aspect() == null ? 0xffffff : key.aspect().getColor();
        int red = (color >> 16) & 255, green = (color >> 8) & 255, blue = color & 255;
        vertices.addVertex(pose, -half, -half, 0).setColor(red,green,blue,255).setUv(0,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,0,1);
        vertices.addVertex(pose, half, -half, 0).setColor(red,green,blue,255).setUv(1,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,0,1);
        vertices.addVertex(pose, half, half, 0).setColor(red,green,blue,255).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,0,1);
        vertices.addVertex(pose, -half, half, 0).setColor(red,green,blue,255).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,0,1);
    }
    @Override public Component getDisplayName(EssentiaKey key) { return key.getDisplayName(); }
}
