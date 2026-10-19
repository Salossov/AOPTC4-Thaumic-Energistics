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
    public static void screens(net.neoforged.neoforge.client.event.RegisterMenuScreensEvent event) {
        event.register(ThaumicEnergistics.BUS_MENU.get(), EssentiaBusScreen::new);
        event.register(ThaumicEnergistics.WORKBENCH_MENU.get(), CellWorkbenchScreen::new);
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
        graphics.blit(texture(key), x, y, 0, 0, 16, 16, 16, 16);
    }
    @Override public void drawOnBlockFace(PoseStack poses, MultiBufferSource buffers, EssentiaKey key, float scale, int light, Level level) {
        var vertices = buffers.getBuffer(RenderType.entityTranslucent(texture(key)));
        var pose = poses.last();
        float half = scale / 2;
        vertices.addVertex(pose, -half, -half, 0).setColor(255,255,255,255).setUv(0,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,0,1);
        vertices.addVertex(pose, half, -half, 0).setColor(255,255,255,255).setUv(1,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,0,1);
        vertices.addVertex(pose, half, half, 0).setColor(255,255,255,255).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,0,1);
        vertices.addVertex(pose, -half, half, 0).setColor(255,255,255,255).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose,0,0,1);
    }
    @Override public Component getDisplayName(EssentiaKey key) { return key.getDisplayName(); }
}
