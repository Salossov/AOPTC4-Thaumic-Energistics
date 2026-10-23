package thaumicenergistics.client;

import appeng.api.orientation.BlockOrientation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.parts.EssentiaStorageMonitorPart;

/** Release screen positions and native aspect tint/alpha on all six part faces. */
public final class EssentiaMonitorRendering {
    private EssentiaMonitorRendering(){}
    public static void render(EssentiaStorageMonitorPart part,PoseStack poses,MultiBufferSource buffers){
        if(!part.isActive()||part.getDisplayed()==null||part.getDisplayed().aspect()==null)return;
        poses.pushPose();poses.translate(.5,.5,.5);poses.mulPose(BlockOrientation.get(part.getSide()).getQuaternion());
        // The release's UP transform faces the same north edge as its horizontal displays.
        if(part.getSide()==net.minecraft.core.Direction.UP)poses.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(180));
        var aspect=part.getDisplayed().aspect();
        quad(poses,buffers,ResourceLocation.parse(aspect.texture()),-.18f,-.13f,.2f,.25f,-.505f,0,1,aspect.getColor(),230);
        quad(poses,buffers,ThaumicEnergistics.id("textures/blocks/parts/monitor.locked.png"),-.3f,.21f,-.2f,.31f,-.508f,
                part.isLocked()?0:.5f,part.isLocked()?.5f:1,0xffffff,128);
        var font=Minecraft.getInstance().font;
        String value=thaumicenergistics.storage.MonitorNumbers.wide(part.getAmount());
        poses.translate(0,-.15,-.52);poses.mulPose(com.mojang.math.Axis.YP.rotationDegrees(180));poses.scale(1/62f,-1/62f,1/62f);
        font.drawInBatch(value,-font.width(value)/2f,0,0xff000000,false,poses.last().pose(),buffers,Font.DisplayMode.NORMAL,0,13631696);
        poses.popPose();
    }
    private static void quad(PoseStack poses,MultiBufferSource buffers,ResourceLocation texture,float x0,float y0,float x1,float y1,float z,float u0,float u1,int color,int alpha){
        // Original renderAspect disables GL lighting: a downward face must not darken its tint.
        // The entity "emissive" shader still mixes directional lights in 1.21.1.
        // The text layer keeps alpha + lightmap but has no normal/diffuse term.
        var v=buffers.getBuffer(RenderType.text(texture));var p=poses.last();int r=color>>16&255,g=color>>8&255,b=color&255;
        v.addVertex(p,x0,y0,z).setColor(r,g,b,alpha).setUv(u1,1).setLight(13631696);
        v.addVertex(p,x0,y1,z).setColor(r,g,b,alpha).setUv(u1,0).setLight(13631696);
        v.addVertex(p,x1,y1,z).setColor(r,g,b,alpha).setUv(u0,0).setLight(13631696);
        v.addVertex(p,x1,y0,z).setColor(r,g,b,alpha).setUv(u0,1).setLight(13631696);
    }
}
