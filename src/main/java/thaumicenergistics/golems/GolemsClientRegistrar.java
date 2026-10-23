package thaumicenergistics.golems;

import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import thaumicenergistics.ThaumicEnergistics;

/**
 * Client-only event subscriber for registering Gearbox BER and Model Layers.
 */
@EventBusSubscriber(modid = ThaumicEnergistics.ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GolemsClientRegistrar {
    public static final ModelLayerLocation GEARBOX_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "gearbox"), "main");

    public static final ModelLayerLocation BACKPACK_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "golem_backpack"), "main");

    private GolemsClientRegistrar() {}

    @SubscribeEvent
    public static void onRegisterLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(GEARBOX_LAYER, GearboxModel::createBodyLayer);
        event.registerLayerDefinition(BACKPACK_LAYER, GolemBackpackModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(GolemsRegistrar.GEARBOX_BE.get(), GearboxBlockEntityRenderer::new);
    }
}
