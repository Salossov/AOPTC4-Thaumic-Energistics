package thaumicenergistics.client;

import appeng.api.orientation.BlockOrientation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.parts.VisInterfacePart;

/** Original rune overlay, tinted by the last successfully drained primal aspect. */
public final class VisInterfaceRendering {
    private VisInterfaceRendering() {}
    public static void render(VisInterfacePart part, PoseStack poses, MultiBufferSource buffers) {
        int color=part.drainColor(); if(color==0)return;
        poses.pushPose();
        poses.translate(.5,.5,.5); poses.mulPose(BlockOrientation.get(part.getSide()).getQuaternion());
        var vertices=buffers.getBuffer(RenderType.entityTranslucent(ThaumicEnergistics.id("textures/blocks/parts/vis.interface.runes.png")));
        var pose=poses.last(); int r=(color>>16)&255,g=(color>>8)&255,b=color&255;
        // Original RenderBlocks cropped UVs to the plate's 4..12 box bounds.
        vertices.addVertex(pose,-.25f,-.25f,-.4376f).setColor(r,g,b,255).setUv(.75f,.75f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(13631696).setNormal(pose,0,0,-1);
        vertices.addVertex(pose,-.25f,.25f,-.4376f).setColor(r,g,b,255).setUv(.75f,.25f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(13631696).setNormal(pose,0,0,-1);
        vertices.addVertex(pose,.25f,.25f,-.4376f).setColor(r,g,b,255).setUv(.25f,.25f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(13631696).setNormal(pose,0,0,-1);
        vertices.addVertex(pose,.25f,-.25f,-.4376f).setColor(r,g,b,255).setUv(.25f,.75f).setOverlay(OverlayTexture.NO_OVERLAY).setLight(13631696).setNormal(pose,0,0,-1);
        poses.popPose();
    }
}
