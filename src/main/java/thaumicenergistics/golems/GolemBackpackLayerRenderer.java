package thaumicenergistics.golems;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.entities.GolemEquipmentApi;
import thaumcraft.api.entities.GolemVisualApi;
import net.minecraft.client.renderer.texture.OverlayTexture;
import thaumcraft.api.entities.IControllableGolem;

/**
 * Client RenderLayer rendering the Wireless Golem Backpack on wearing golems.
 * Reads GolemEquipmentApi.backpack(entity) directly on client without server map dependency.
 * Attachment, scale, and pearl rotation match 1.7.10 WirelessGolemHandler.renderGolem.
 */
public class GolemBackpackLayerRenderer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private final GolemBackpackModel backpackModel;
    private final Map<LivingEntity, PearlAnimation> animations = new WeakHashMap<>();

    private static final class PearlAnimation {
        float rotation;
    }

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

        // LivingEntityRenderer has already applied RY(180-bodyYaw), S(-1,-1,1)
        // and T(0,-1.501,0). Undo its model flip, keeping the inherited body yaw:
        // the source attachment is world Y+0.64, RY(270-bodyYaw), local X-0.16.
        poseStack.translate(0.0D, 1.501D - 0.64D, 0.0D);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
        poseStack.translate(-0.16D, 0.0D, 0.0D);
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

        var visual = GolemVisualApi.state(entity);
        boolean active = GolemWirelessStatusPayload.inRange(entity) && visual.supported() && !visual.inactive();
        int packColor = entity.hurtTime > 0 ? 0xFFFF0000 : visual.healingTicks() > 0 ? 0xFF7FFF7F : 0xFFFFFFFF;
        var animation = animations.computeIfAbsent(entity, ignored -> new PearlAnimation());
        if (active && !Minecraft.getInstance().isPaused()) {
            // Quirk kept: WirelessGolemHandler.renderGolem accumulates each render
            // partial, rather than measuring elapsed entity ticks between frames.
            animation.rotation += partialTick * 2.0F;
            if (animation.rotation >= 360.0F) animation.rotation -= 360.0F;
        }
        this.backpackModel.renderBackpack(poseStack, buffer, packedLight,
                OverlayTexture.NO_OVERLAY, animation.rotation, active, packColor);

        poseStack.popPose();
    }
}
