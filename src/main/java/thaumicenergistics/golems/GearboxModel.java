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
 * 1.21.1 Model definition for Iron and Thaumium Gearbox.
 * Recreates original 1.7.10 ModelGearbox with 7x7x7 transmission box,
 * 6 directional shafts, and 4-spoke gears on detached sides.
 * Shaft offsets calibrated for PoseStack space with EDGE_DISTANCE = 0.312F and SPOKE_OFFSET = 1.2F.
 */
public class GearboxModel extends Model {
    private static final float HALF_TURN = (float) (Math.PI / 2);
    private static final float QUARTER_TURN = (float) (Math.PI / 4);
    private static final float EDGE_DISTANCE = 0.312F;
    private static final float SPOKE_OFFSET = 1.2F;

    private final ModelPart transmission;
    private final ModelPart shaft;
    private final ModelPart spoke;

    private static class SideTransform {
        final float rotY, rotZ;
        final float shaftX, shaftY, shaftZ;
        final float spokeX, spokeY, spokeZ;

        SideTransform(float rotY, float rotZ, float x, float y, float z) {
            this.rotY = rotY;
            this.rotZ = rotZ;
            this.shaftX = x;
            this.shaftY = y;
            this.shaftZ = z;
            this.spokeX = x * SPOKE_OFFSET;
            this.spokeY = y * SPOKE_OFFSET;
            this.spokeZ = z * SPOKE_OFFSET;
        }
    }

    private static final SideTransform[] TRANSFORMS = new SideTransform[6];
    static {
        // Match 1.7.10 ForgeDirection ordering:
        // 0=DOWN, 1=UP, 2=NORTH, 3=SOUTH, 4=WEST, 5=EAST
        TRANSFORMS[0] = new SideTransform(0F, HALF_TURN, 0F, -EDGE_DISTANCE, 0F); // DOWN
        TRANSFORMS[1] = new SideTransform(0F, HALF_TURN, 0F, EDGE_DISTANCE, 0F);  // UP
        TRANSFORMS[2] = new SideTransform(HALF_TURN, 0F, 0F, 0F, -EDGE_DISTANCE); // NORTH
        TRANSFORMS[3] = new SideTransform(HALF_TURN, 0F, 0F, 0F, EDGE_DISTANCE);  // SOUTH
        TRANSFORMS[4] = new SideTransform(0F, 0F, -EDGE_DISTANCE, 0F, 0F);         // WEST
        TRANSFORMS[5] = new SideTransform(0F, 0F, EDGE_DISTANCE, 0F, 0F);          // EAST
    }

    public GearboxModel(ModelPart root) {
        super(RenderType::entitySolid);
        this.transmission = root.getChild("transmission");
        this.shaft = root.getChild("shaft");
        this.spoke = root.getChild("spoke");
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        // Transmission box 7x7x7, centered at -3.5, -3.5, -3.5
        root.addOrReplaceChild("transmission",
                CubeListBuilder.create().texOffs(1, 0).mirror()
                        .addBox(-3.5F, -3.5F, -3.5F, 7.0F, 7.0F, 7.0F),
                PartPose.ZERO);

        // Shaft 3x1x1
        root.addOrReplaceChild("shaft",
                CubeListBuilder.create().texOffs(1, 15).mirror()
                        .addBox(-1.5F, -0.5F, -0.5F, 3.0F, 1.0F, 1.0F),
                PartPose.ZERO);

        // Spoke 1x7x1
        root.addOrReplaceChild("spoke",
                CubeListBuilder.create().texOffs(1, 18).mirror()
                        .addBox(-0.5F, -3.5F, -0.5F, 1.0F, 7.0F, 1.0F),
                PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override
    public void renderToBuffer(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, int color) {
        this.transmission.render(poseStack, buffer, packedLight, packedOverlay, color);
    }

    public void renderGearbox(PoseStack poseStack, VertexConsumer buffer, int packedLight, int packedOverlay, float rotationAngle, boolean[] facingCrankable) {
        int color = 0xFFFFFFFF;

        // Render transmission center
        this.transmission.render(poseStack, buffer, packedLight, packedOverlay, color);

        // Render each side's shaft and gears
        for (int side = 0; side < 6; side++) {
            SideTransform transform = TRANSFORMS[side];
            boolean withGear = facingCrankable == null || !facingCrankable[side];

            poseStack.pushPose();
            poseStack.translate(transform.shaftX, transform.shaftY, transform.shaftZ);
            if (transform.rotY != 0) {
                poseStack.mulPose(Axis.YP.rotation(transform.rotY));
            }
            if (transform.rotZ != 0) {
                poseStack.mulPose(Axis.ZP.rotation(transform.rotZ));
            }
            poseStack.mulPose(Axis.XP.rotation(rotationAngle));

            // Render shaft
            this.shaft.render(poseStack, buffer, packedLight, packedOverlay, color);
            poseStack.popPose();

            // Render 4-spoke gear if not connected to a crankable
            if (withGear) {
                poseStack.pushPose();
                // ModelRenderer.offset* was applied before rotation in 1.7.10.
                // Translating a world-space difference after the shaft rotation
                // moves north/south/up/down gears onto the wrong axis.
                poseStack.translate(transform.spokeX, transform.spokeY, transform.spokeZ);
                if (transform.rotY != 0) poseStack.mulPose(Axis.YP.rotation(transform.rotY));
                if (transform.rotZ != 0) poseStack.mulPose(Axis.ZP.rotation(transform.rotZ));
                poseStack.mulPose(Axis.XP.rotation(rotationAngle));
                for (int sp = 0; sp < 4; sp++) {
                    poseStack.mulPose(Axis.XP.rotation(QUARTER_TURN));
                    this.spoke.render(poseStack, buffer, packedLight, packedOverlay, color);
                }
                poseStack.popPose();
            }
        }
    }
}
