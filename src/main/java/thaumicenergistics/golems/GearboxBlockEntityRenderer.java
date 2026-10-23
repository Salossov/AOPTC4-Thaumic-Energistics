package thaumicenergistics.golems;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics.ThaumicEnergistics;

/**
 * 1.21.1 BlockEntityRenderer for Iron and Thaumium Gearbox.
 * Faithfully mirrors 1.7.10 RenderTileGearbox geometry, textures, and scaling (0.12 * 0.625 = 0.075 blocks, scaled by 1.2F in PoseStack).
 */
public class GearboxBlockEntityRenderer implements BlockEntityRenderer<GearboxBlockEntity> {
    public static final ResourceLocation TEX_IRON = ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "textures/models/gearbox_iron.png");
    public static final ResourceLocation TEX_THAUMIUM = ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "textures/models/gearbox_thaumium.png");

    private final GearboxModel model;

    public GearboxBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new GearboxModel(context.bakeLayer(GolemsClientRegistrar.GEARBOX_LAYER));
    }

    @Override
    public void render(GearboxBlockEntity gearbox, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (thaumicenergistics.config.ThEClientConfig.DISABLE_GEARBOX_MODEL.get()) {
            GearboxFallbackRendering.render(gearbox.getBlockState(), poseStack, bufferSource, packedLight, packedOverlay);
            return;
        }
        poseStack.pushPose();

        // Center model in the block space
        poseStack.translate(0.5D, 0.5D, 0.5D);

        // Model scale: 1.2F (matching 0.12 * 0.625 = 0.075 blocks)
        poseStack.scale(1.2F, 1.2F, 1.2F);

        ResourceLocation texture = gearbox.isThaumiumGearbox() ? TEX_THAUMIUM : TEX_IRON;
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entitySolid(texture));

        this.model.renderGearbox(poseStack, buffer, packedLight, packedOverlay, gearbox.shaftRotation, gearbox.sideIsFacingCrankable);

        poseStack.popPose();
    }
}
