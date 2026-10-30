package thaumicenergistics.golems;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import thaumicenergistics.ThaumicEnergistics;

/**
 * Client-only event subscriber for registering Gearbox BER, Model Layers, and Golem Backpack RenderLayer.
 */
@EventBusSubscriber(modid = ThaumicEnergistics.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GolemsClientRegistrar {
    public static final ModelLayerLocation GEARBOX_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "gearbox"), "main");

    public static final ModelLayerLocation BACKPACK_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "golem_backpack"), "main");

    private GolemsClientRegistrar() {}

    @SubscribeEvent
    public static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener) resources -> GearboxItemRenderer.invalidateModels());
    }

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GEARBOX_LAYER, GearboxModel::createBodyLayer);
        event.registerLayerDefinition(BACKPACK_LAYER, GolemBackpackModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GolemsRegistrar.GEARBOX_BE.get(), GearboxBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        for (EntityType<?> type : event.getEntityTypes()) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if ("thaumcraft".equals(key.getNamespace()) && "golem".equals(key.getPath())) {
                var renderer = event.getRenderer(type);
                if (renderer instanceof LivingEntityRenderer<?, ?> livingRenderer) {
                    addLayerHelper(livingRenderer, event);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends LivingEntity, M extends EntityModel<T>> void addLayerHelper(
            LivingEntityRenderer<?, ?> renderer, EntityRenderersEvent.AddLayers event) {
        LivingEntityRenderer<T, M> typedRenderer = (LivingEntityRenderer<T, M>) renderer;
        typedRenderer.addLayer(new GolemBackpackLayerRenderer<>(
                typedRenderer,
                new GolemBackpackModel(event.getEntityModels().bakeLayer(BACKPACK_LAYER))));
    }
}
