package thaumicenergistics.client;

import appeng.api.orientation.BlockOrientation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.parts.EssentiaIOBusPart;

/** The release draws chamber glass in alpha pass 1; AE2's static parts use cutout. */
public final class EssentiaBusRendering {
    private EssentiaBusRendering() {}

    public static void render(EssentiaIOBusPart part, PoseStack poses, MultiBufferSource buffers,
            int light, int overlay) {
        poses.pushPose();
        poses.translate(.5, .5, .5);
        poses.mulPose(BlockOrientation.get(part.getSide()).getQuaternion());
        poses.translate(-.5, -.5, -.5);
        var texture = ThaumicEnergistics.id("textures/blocks/parts/essentia."
                + (part.importing() ? "import" : "export") + ".bus.chamber.png");
        var vertices = buffers.getBuffer(RenderType.entityTranslucent(texture));
        if (part.importing()) {
            chamber(poses, vertices, 4, 4, 1, 12, 12, 2, false, true, light, overlay);
            chamber(poses, vertices, 5, 5, 2, 11, 11, 3, false, false, light, overlay);
        } else {
            chamber(poses, vertices, 4, 4, 2.5f, 12, 12, 3.5f, true, false, light, overlay);
            chamber(poses, vertices, 5, 5, 1.5f, 11, 11, 2.5f, false, false, light, overlay);
        }
        poses.popPose();
    }

    private static void chamber(PoseStack poses, VertexConsumer out, float x0, float y0, float z0,
            float x1, float y1, float z1, boolean north, boolean south, int light, int overlay) {
        // Same outward winding and UV projection as the north-facing baked cuboids.
        if (north) face(poses,out, new float[][]{{x1,y1,z0},{x1,y0,z0},{x0,y0,z0},{x0,y1,z0}},
                new float[]{16-x1,16-y1,16-x0,16-y0},0,0,-1,light,overlay);
        if (south) face(poses,out, new float[][]{{x0,y1,z1},{x0,y0,z1},{x1,y0,z1},{x1,y1,z1}},
                new float[]{x0,16-y1,x1,16-y0},0,0,1,light,overlay);
        face(poses,out, new float[][]{{x1,y1,z1},{x1,y0,z1},{x1,y0,z0},{x1,y1,z0}},
                new float[]{16-z1,16-y1,16-z0,16-y0},1,0,0,light,overlay);
        face(poses,out, new float[][]{{x0,y1,z0},{x0,y0,z0},{x0,y0,z1},{x0,y1,z1}},
                new float[]{z0,16-y1,z1,16-y0},-1,0,0,light,overlay);
        face(poses,out, new float[][]{{x0,y1,z0},{x0,y1,z1},{x1,y1,z1},{x1,y1,z0}},
                new float[]{x0,z0,x1,z1},0,1,0,light,overlay);
        face(poses,out, new float[][]{{x0,y0,z1},{x0,y0,z0},{x1,y0,z0},{x1,y0,z1}},
                new float[]{x0,16-z1,x1,16-z0},0,-1,0,light,overlay);
    }

    private static void face(PoseStack poses, VertexConsumer out, float[][] points, float[] uv,
            float nx, float ny, float nz, int light, int overlay) {
        var pose = poses.last();
        for (int i = 0; i < 4; i++) {
            float u = i < 2 ? uv[0] : uv[2];
            float v = i == 0 || i == 3 ? uv[1] : uv[3];
            out.addVertex(pose, points[i][0]/16, points[i][1]/16, points[i][2]/16)
                    .setColor(255,255,255,255).setUv(u/16,v/16).setOverlay(overlay)
                    .setLight(light).setNormal(pose,nx,ny,nz);
        }
    }
}
