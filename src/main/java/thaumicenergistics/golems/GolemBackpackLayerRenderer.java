package thaumicenergistics.golems;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Client RenderLayer rendering the Wireless Golem Backpack on wearing golems.
 * Attachment, scale, and pearl rotation match 1.7.10 WirelessGolemHandler.renderGolem.
 */
public class GolemBackpackLayerRenderer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final GolemBackpackModel backpackModel;

    public GolemBackpackLayerRenderer(RenderLayerParent<T, M> renderer, GolemBackpackModel backpackModel) {
        super(renderer);
        this.backpackModel = backpackModel;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, T entity,
                       float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        var conn = GolemBackpackManager.getConnection(entity);
        if (conn == null) {
            return;
        }

        poseStack.pushPose();

        // 1.7.10 attachment offset: Y + 0.64, offset behind golem: Z - 0.16, scale 0.8F
        poseStack.translate(0.0D, 0.64D, -0.16D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.8F, 0.8F, 0.8F);

        GolemBackpackSkin skin = conn.skin != null ? conn.skin : GolemBackpackSkin.THAUMIUM;
        ResourceLocation texture = skin.getTextureLocation();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));

        float pearlRotation = (ageInTicks * 2.0F) % 360.0F;
        this.backpackModel.renderBackpack(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, pearlRotation, conn.isInRange);

        poseStack.popPose();
    }
}
