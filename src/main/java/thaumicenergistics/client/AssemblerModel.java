package thaumicenergistics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics.ThaumicEnergistics;

/** Original 64x64 model and rotations, rendered as opaque frame then translucent glass. */
public final class AssemblerModel {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ThaumicEnergistics.id("arcane_assembler"),"main");
    private static final ResourceLocation TEXTURE=ThaumicEnergistics.id("textures/models/arcane.assembler.png");
    private static final float HALF_TURN=1.570796325F;
    private final ModelPart outerTop,outerBottom,outerLeft,outerRight,innerTop,innerBottom,innerLeft,innerRight,glass;
    public AssemblerModel(ModelPart root){
        outerTop=root.getChild("outer_top");outerBottom=root.getChild("outer_bottom");outerLeft=root.getChild("outer_left");outerRight=root.getChild("outer_right");
        innerTop=root.getChild("inner_top");innerBottom=root.getChild("inner_bottom");innerLeft=root.getChild("inner_left");innerRight=root.getChild("inner_right");glass=root.getChild("glass");
    }
    private static void box(PartDefinition root,String name,int u,int v,float x,float y,float z,int width,int height,int depth,float rotateZ){
        root.addOrReplaceChild(name,CubeListBuilder.create().texOffs(u,v).mirror().addBox(x,y,z,width,height,depth),PartPose.rotation(0,0,rotateZ));
    }
    public static LayerDefinition layer(){
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        box(root,"outer_top",0,0,-17,-17,-15,34,2,2,0);
        box(root,"outer_bottom",0,0,-17,15,-15,34,2,2,0);
        box(root,"outer_left",0,0,-17,-17,-15,34,2,2,-1.570796F);
        box(root,"outer_right",0,0,-17,-17,-15,34,2,2,1.570796F);
        box(root,"inner_top",0,4,-15,-15,-15,30,2,2,0);
        box(root,"inner_bottom",0,4,-15,13,-15,30,2,2,0);
        box(root,"inner_left",0,4,-15,-15,-15,30,2,2,-1.570796F);
        box(root,"inner_right",0,4,-15,-15,-15,30,2,2,1.570796F);
        box(root,"glass",0,37,-13,-13,-14,26,26,1,0);
        return LayerDefinition.create(mesh,64,64);
    }
    public void render(PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        poses.pushPose();
        // The release applies .047 then ModelRenderer's .625; ModelPart already divides its cubes by16.
        poses.translate(.5,.5,.5);poses.scale(.047F*.625F*16,.047F*.625F*16,.047F*.625F*16);
        var solid=buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        for(float rotation:new float[]{0,HALF_TURN,-HALF_TURN,HALF_TURN+HALF_TURN}){
            outerTop.yRot=rotation;outerBottom.yRot=rotation;outerLeft.xRot=-rotation;outerRight.xRot=rotation;
            outerTop.render(poses,solid,light,overlay);outerBottom.render(poses,solid,light,overlay);outerLeft.render(poses,solid,light,overlay);outerRight.render(poses,solid,light,overlay);
        }
        float[][] faces={{0,0},{HALF_TURN,0},{-HALF_TURN,0},{HALF_TURN+HALF_TURN,0},{0,HALF_TURN},{0,-HALF_TURN}};
        for(var face:faces){
            for(var part:new ModelPart[]{innerTop,innerBottom,innerLeft,innerRight}){part.yRot=face[0];part.xRot=face[1];part.render(poses,solid,light,overlay);}
        }
        var translucent=buffers.getBuffer(RenderType.entityTranslucent(TEXTURE));
        for(var face:faces){glass.yRot=face[0];glass.xRot=face[1];glass.render(poses,translucent,light,overlay);}
        poses.popPose();
    }
}
