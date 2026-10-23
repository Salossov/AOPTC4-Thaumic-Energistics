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
 * Faithfully mirrors 1.7.10 ModelGolemWifiBackpack geometry (Antenna, PackBack, PackFront, Pearl).
 */
public class GolemBackpackModel extends Model {
    private final ModelPart antenna;
    private final ModelPart packBack;
    private final ModelPart packFront;
    private final ModelPart pearl;

    public GolemBackpackModel(ModelPart root) {
        super(RenderType::entityCutoutNoCull);
        this.antenna = root.getChild("antenna");
        this.packBack = root.getChild("pack_back");
        this.packFront = root.getChild("pack_front");
        this.pearl = root.getChild("pearl");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Antenna: 1x3x1 at (-0.5, -6, -0.5)
        root.addOrReplaceChild("antenna",
                CubeListBuilder.create().texOffs(10, 0)
                        .addBox(-0.5F, -6.0F, -0.5F, 1.0F, 3.0F, 1.0F),
                PartPose.rotation((float) Math.PI, 0, 0));

        // PackBack: 2x6x6 at (-1, -3, -3)
        root.addOrReplaceChild("pack_back",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-1.0F, -3.0F, -3.0F, 2.0F, 6.0F, 6.0F),
                PartPose.rotation((float) Math.PI, 0, 0));

        // PackFront: 1x2x4 at (-1.5, -1, -2)
        root.addOrReplaceChild("pack_front",
                CubeListBuilder.create().texOffs(2, 0)
                        .addBox(-1.5F, -1.0F, -2.0F, 1.0F, 2.0F, 4.0F),
                PartPose.rotation((float) Math.PI, 0, 0));

        // Floating Fluix Pearl cube atop the antenna at Y = -7.5
        root.addOrReplaceChild("pearl",
                CubeListBuilder.create().texOffs(8, 6)
                        .addBox(-1.0F, -1.0F, -1.0F, 2.0F, 2.0F, 2.0F),
                PartPose.offset(0.0F, 6.5F, 0.0F));

        return LayerDefinition.create(mesh, 16, 16);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        this.packBack.render(poseStack, buffer, packedLight, packedOverlay, color);
        this.packFront.render(poseStack, buffer, packedLight, packedOverlay, color);
        this.antenna.render(poseStack, buffer, packedLight, packedOverlay, color);
        this.pearl.render(poseStack, buffer, packedLight, packedOverlay, color);
    }

    public void renderBackpack(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float pearlRotation, boolean inRange) {
        int packColor = 0xFFFFFFFF;
        this.packBack.render(poseStack, buffer, packedLight, packedOverlay, packColor);
        this.packFront.render(poseStack, buffer, packedLight, packedOverlay, packColor);
        this.antenna.render(poseStack, buffer, packedLight, packedOverlay, packColor);

        // Render rotating pearl with range tint (green/white when in range, red when disconnected)
        poseStack.pushPose();
        poseStack.translate(0.0F, 6.5F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(pearlRotation));
        poseStack.scale(0.8F, 0.8F, 0.8F);

        int pearlColor = inRange ? 0xFFFFFFFF : 0xFFFF3333;
        this.pearl.render(poseStack, buffer, packedLight, packedOverlay, pearlColor);
        poseStack.popPose();
    }
}
