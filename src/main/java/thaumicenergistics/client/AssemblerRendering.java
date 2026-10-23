package thaumicenergistics.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.assembler.AssemblerRegistration;

@EventBusSubscriber(modid=ThaumicEnergistics.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class AssemblerRendering {
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event){event.registerLayerDefinition(AssemblerModel.LAYER,AssemblerModel::layer);}
    @SubscribeEvent public static void screens(RegisterMenuScreensEvent event){event.register(AssemblerRegistration.MENU.get(),ArcaneAssemblerScreen::new);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){
        event.registerBlockEntityRenderer(AssemblerRegistration.BLOCK_ENTITY.get(),context->new net.minecraft.client.renderer.blockentity.BlockEntityRenderer<thaumicenergistics.assembler.ArcaneAssemblerBlockEntity>(){
            private final AssemblerModel model=new AssemblerModel(context.bakeLayer(AssemblerModel.LAYER));
            @Override public void render(thaumicenergistics.assembler.ArcaneAssemblerBlockEntity assembler,float partialTicks,
                    com.mojang.blaze3d.vertex.PoseStack poses,net.minecraft.client.renderer.MultiBufferSource buffers,int light,int overlay){
                model.render(poses,buffers,light,overlay);
            }
        });
    }
    private AssemblerRendering(){}
}
