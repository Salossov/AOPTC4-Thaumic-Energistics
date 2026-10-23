package thaumicenergistics.golems;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.RenderType;

/**
 * 1.21.1 Model definition for the Wireless Golem Backpack.
 * Original three model parts and four double-sided pearl panels, in block units.
 */
public class GolemBackpackModel extends Model {
    private final ModelPart antenna;
    private final ModelPart packBack;
    private final ModelPart packFront;

    public GolemBackpackModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.antenna = root.getChild("antenna");
        this.packBack = root.getChild("pack_back");
        this.packFront = root.getChild("pack_front");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Antenna: 1x3x1 at (-0.5, -6, -0.5)
        root.addOrReplaceChild("antenna",
                CubeListBuilder.create().texOffs(10, 0).mirror()
                        .addBox(-0.5F, -6.0F, -0.5F, 1.0F, 3.0F, 1.0F),
                PartPose.rotation(3.14F, 0, 0));

        // PackBack: 2x6x6 at (-1, -3, -3)
        root.addOrReplaceChild("pack_back",
                CubeListBuilder.create().texOffs(0, 0).mirror()
                        .addBox(-1.0F, -3.0F, -3.0F, 2.0F, 6.0F, 6.0F),
                PartPose.rotation(3.14F, 0, 0));

        // PackFront: 1x2x4 at (-1.5, -1, -2)
        root.addOrReplaceChild("pack_front",
                CubeListBuilder.create().texOffs(2, 0).mirror()
                        .addBox(-1.5F, -1.0F, -2.0F, 1.0F, 2.0F, 4.0F),
                PartPose.rotation(3.14F, 0, 0));

        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        this.packBack.render(poseStack, buffer, packedLight, packedOverlay, color);
        this.packFront.render(poseStack, buffer, packedLight, packedOverlay, color);
        this.antenna.render(poseStack, buffer, packedLight, packedOverlay, color);
        renderPearl(poseStack, buffer, packedLight, packedOverlay, 0.0F, color);
    }

    public void renderBackpack(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float pearlRotation, boolean inRange) {
        int packColor = 0xFFFFFFFF;
        this.packBack.render(poseStack, buffer, packedLight, packedOverlay, packColor);
        this.packFront.render(poseStack, buffer, packedLight, packedOverlay, packColor);
        this.antenna.render(poseStack, buffer, packedLight, packedOverlay, packColor);

        renderPearl(poseStack, buffer, packedLight, packedOverlay, pearlRotation,
                inRange ? 0xFFFFFFFF : 0xFFFF0000);
    }

    private static void renderPearl(PoseStack poseStack, VertexConsumer buffer, int light, int overlay,
                                    float rotation, int color) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.YP.rotationDegrees(rotation));
        poseStack.translate(-0.0955F, 0.275F, -0.045F);
        poseStack.scale(0.2F, 0.2F, 0.2F);
        float minU = 8.0F / 16, maxU = 13.5F / 16;
        float faceDistance = -0.013F / 0.2F;
        drawPearlFaces(poseStack, buffer, light, overlay, color, minU, maxU);
        poseStack.translate(0, 0, 0.32F - faceDistance * 2);
        drawPearlFaces(poseStack, buffer, light, overlay, color, maxU, minU);
        poseStack.mulPose(Axis.YP.rotationDegrees(90));
        poseStack.translate(-0.32F - faceDistance, 0, 0.32F + faceDistance);
        drawPearlFaces(poseStack, buffer, light, overlay, color, maxU, minU);
        poseStack.translate(0, 0, 0.32F - faceDistance * 2);
        drawPearlFaces(poseStack, buffer, light, overlay, color, minU, maxU);
        poseStack.popPose();
    }

    private static void drawPearlFaces(PoseStack pose, VertexConsumer buffer, int light, int overlay,
                                       int color, float minU, float maxU) {
        float minV = 6.0F / 16, maxV = 11.5F / 16;
        pearlVertex(pose, buffer, light, overlay, color, 0, 0, maxU, maxV, 1);
        pearlVertex(pose, buffer, light, overlay, color, 1, 0, minU, maxV, 1);
        pearlVertex(pose, buffer, light, overlay, color, 1, 1, minU, minV, 1);
        pearlVertex(pose, buffer, light, overlay, color, 0, 1, maxU, minV, 1);
        pearlVertex(pose, buffer, light, overlay, color, 0, 1, maxU, minV, -1);
        pearlVertex(pose, buffer, light, overlay, color, 1, 1, minU, minV, -1);
        pearlVertex(pose, buffer, light, overlay, color, 1, 0, minU, maxV, -1);
        pearlVertex(pose, buffer, light, overlay, color, 0, 0, maxU, maxV, -1);
    }

    private static void pearlVertex(PoseStack pose, VertexConsumer buffer, int light, int overlay,
                                    int color, float x, float y, float u, float v, float normalZ) {
        buffer.addVertex(pose.last(), x, y, 0).setColor(color).setUv(u, v)
                .setOverlay(overlay).setLight(light).setNormal(pose.last(), 0, 0, normalZ);
    }
}
