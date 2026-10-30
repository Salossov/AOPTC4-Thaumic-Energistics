package thaumicenergistics.client;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.blocks.*;

/** RenderBlockProviderBase's second pass: translucent cable-colored runes. */
public final class ProviderRendering<T extends ProviderBlockEntity> implements BlockEntityRenderer<T> {
    private final ResourceLocation overlay;
    public ProviderRendering(String type){overlay=ThaumicEnergistics.id("textures/blocks/"+type+".provider.overlay.png");}
    @Override public void render(T provider,float partial,PoseStack poses,MultiBufferSource buffers,int light,int overlayLight){
        int color=provider.isActive()?provider.getColor().mediumVariant:0;
        int packed=provider.isActive()?LightTexture.FULL_BRIGHT:light;
        var vertices=buffers.getBuffer(RenderType.text(overlay));var pose=poses.last();
        // Separate the two original coplanar passes by a subpixel distance to
        // keep the modern depth buffer from producing flickering black edges.
        float low=-.0001F,high=1.0001F;
        face(vertices,pose,color,packed,new float[][]{{low,low,low,0,1},{high,low,low,1,1},{high,low,high,1,0},{low,low,high,0,0}});
        face(vertices,pose,color,packed,new float[][]{{high,low,low,1,1},{high,high,low,1,0},{high,high,high,0,0},{high,low,high,0,1}});
        face(vertices,pose,color,packed,new float[][]{{low,low,high,0,1},{high,low,high,1,1},{high,high,high,1,0},{low,high,high,0,0}});
        face(vertices,pose,color,packed,new float[][]{{low,high,low,1,0},{high,high,low,0,0},{high,low,low,0,1},{low,low,low,1,1}});
        face(vertices,pose,color,packed,new float[][]{{low,high,high,1,0},{high,high,high,0,0},{high,high,low,0,1},{low,high,low,1,1}});
        face(vertices,pose,color,packed,new float[][]{{low,low,high,1,1},{low,high,high,1,0},{low,high,low,0,0},{low,low,low,0,1}});
    }
    private static void face(VertexConsumer vertices,PoseStack.Pose pose,int color,int light,float[][] points){
        int red=color>>16&255,green=color>>8&255,blue=color&255;
        for(var point:points)vertices.addVertex(pose,point[0],point[1],point[2]).setColor(red,green,blue,255).setUv(point[3],point[4]).setLight(light);
    }
    @EventBusSubscriber(modid=ThaumicEnergistics.MOD_ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(EntityRenderersEvent.RegisterRenderers event){
            event.registerBlockEntityRenderer(ThaumicEnergistics.ESSENTIA_PROVIDER_ENTITY.get(),context->new ProviderRendering<>("essentia"));
            event.registerBlockEntityRenderer(ThaumicEnergistics.INFUSION_PROVIDER_ENTITY.get(),context->new ProviderRendering<>("infusion"));
        }
    }
}
