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
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.entities.GolemEquipmentApi;
import thaumcraft.api.entities.IControllableGolem;

/**
 * Client RenderLayer rendering the Wireless Golem Backpack on wearing golems.
 * Reads GolemEquipmentApi.backpack(entity) directly on client without server map dependency.
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
        if (!GolemEquipmentApi.supports(entity)) {
            return;
        }

        ItemStack backpack = GolemEquipmentApi.backpack(entity);
        if (backpack.isEmpty() || !(backpack.getItem() instanceof WirelessGolemBackpackItem)) {
            return;
        }

        poseStack.pushPose();

        // 1.7.10 attachment offset: Y + 0.64, offset behind golem: Z - 0.16, scale 0.8F
        poseStack.translate(0.0D, 0.64D, -0.16D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        poseStack.scale(0.8F, 0.8F, 0.8F);

        GolemBackpackSkin skin = WirelessGolemBackpackItem.getSkin(backpack);
        if (skin == null && entity instanceof IControllableGolem cg) {
            skin = GolemBackpackSkin.fromMaterialName(cg.golemMaterial());
        }
        if (skin == null) {
            skin = GolemBackpackSkin.THAUMIUM;
        }

        ResourceLocation texture = skin.getTextureLocation();
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(texture));

        float pearlRotation = (ageInTicks * 2.0F) % 360.0F;
        boolean inRange = WirelessGolemBackpackItem.isLinked(backpack);
        this.backpackModel.renderBackpack(poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, pearlRotation, inRange);

        poseStack.popPose();
    }
}
