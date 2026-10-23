package thaumicenergistics.golems;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Custom Item Renderer for Gearbox BlockItems, rendering the 3D model in inventory and hands.
 * Model scale: 1.2F (matching 0.12 * 0.625 = 0.075 blocks).
 */
public class GearboxItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final boolean isThaumium;
    private GearboxModel model;

    public GearboxItemRenderer(boolean isThaumium) {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
        this.isThaumium = isThaumium;
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
        if (this.model == null) {
            this.model = new GearboxModel(Minecraft.getInstance().getEntityModels().bakeLayer(GolemsClientRegistrar.GEARBOX_LAYER));
        }

        poseStack.pushPose();
        poseStack.translate(0.5D, 0.5D, 0.5D);
        poseStack.scale(1.2F, 1.2F, 1.2F);

        ResourceLocation texture = isThaumium ? GearboxBlockEntityRenderer.TEX_THAUMIUM : GearboxBlockEntityRenderer.TEX_IRON;
        VertexConsumer consumer = buffer.getBuffer(RenderType.entitySolid(texture));
        this.model.renderGearbox(poseStack, consumer, packedLight, packedOverlay, 0.0F, null);

        poseStack.popPose();
    }
}
