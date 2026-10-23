package thaumicenergistics.client;

import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.assembler.*;

/** Original 234/247x197 screen, six primal bars, crafting progress and source tooltip units. */
public final class ArcaneAssemblerScreen extends AbstractContainerScreen<ArcaneAssemblerMenu> {
    private static final ResourceLocation TEXTURE=ThaumicEnergistics.id("textures/gui/arcane.assembler.png");
    public ArcaneAssemblerScreen(ArcaneAssemblerMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=menu.hasNetworkTool()?247:234;imageHeight=197;}
    @Override protected void containerTick(){super.containerTick();menu.clientTick();}
    private void bar(GuiGraphics graphics,int index,float fraction){
        int height=(int)Math.floor(Math.max(0,Math.min(1,fraction))*16);if(height<=0)return;int x=41+index*18;
        GuiTextures.blit(graphics,TEXTURE,leftPos+x,topPos+87+16-height,x,202+16-height,4,height);
    }
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY){
        if(menu.hasNetworkTool())GuiTextures.blit(graphics,TEXTURE,leftPos,topPos,0,0,imageWidth,imageHeight);
        else{GuiTextures.blit(graphics,TEXTURE,leftPos,topPos,0,0,176,197);GuiTextures.blit(graphics,TEXTURE,leftPos+176,topPos,176,0,58,104);}
        for(int i=0;i<6;i++)bar(graphics,i,menu.state().vis().get(i)/(float)AssemblerRules.MAX_VIS);
        bar(graphics,6,menu.progress());
    }
    @Override protected void renderLabels(GuiGraphics graphics,int mouseX,int mouseY){graphics.drawString(font,title,6,6,0,false);}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
        super.render(graphics,mouseX,mouseY,partial);renderTooltip(graphics,mouseX,mouseY);
        for(int i=0;i<6;i++)if(isHovering(41+i*18,87,4,16,mouseX,mouseY)){
            var aspect=Aspect.getAspect(AssemblerVisBuffer.PRIMALS.get(i));if(aspect==null)break;
            Component name=aspect.translationKey()==null?Component.literal(aspect.getName()):Component.translatable(aspect.translationKey());
            var lines=List.of(name.copy().withStyle(style->style.withColor(aspect.getColor())),Component.literal(menu.state().vis().get(i)/10F+" / 150"),
                    Component.literal(String.format(Locale.getDefault(),"%.0f%% ",menu.state().modifiers().get(i)*100F)).append(Component.translatable("tc.vis.costavg")));
            graphics.renderComponentTooltip(font,lines,mouseX,mouseY);break;
        }
    }
}
