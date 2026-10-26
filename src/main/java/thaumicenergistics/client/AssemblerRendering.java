package thaumicenergistics.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.assembler.AssemblerRegistration;

@EventBusSubscriber(modid=ThaumicEnergistics.ID,value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class AssemblerRendering {
    @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent event){
        event.registerReloadListener((ResourceManagerReloadListener)resources->{AssemblerItemRenderer.invalidateModels();net.minecraft.client.Minecraft.getInstance().execute(AssemblerAnimationEffects::clear);});
    }
    @SubscribeEvent public static void setup(net.neoforged.fml.event.lifecycle.FMLClientSetupEvent event){
        event.enqueueWork(AssemblerAnimationEffects::install);
    }
    @SubscribeEvent public static void particles(net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent event){
        event.registerSpriteSet(thaumicenergistics.assembler.AssemblerParticles.ENERGY.get(),sprites->{
            AssemblerAnimationEffects.sprites(sprites);
            return (type,level,x,y,z,vx,vy,vz)->new AssemblerCraftingParticle(level,x,y,z,vx,vy,vz,sprites);
        });
    }
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
