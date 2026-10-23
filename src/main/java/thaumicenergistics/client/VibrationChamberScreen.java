package thaumicenergistics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics.menu.VibrationChamberMenu;

public final class VibrationChamberScreen extends AbstractContainerScreen<VibrationChamberMenu>{
    private static final ResourceLocation BACKGROUND=ResourceLocation.parse("thaumicenergistics:textures/gui/essentia.vibration.chamber.png");
    private static final ResourceLocation ALCHEMY=ResourceLocation.parse("thaumcraft:textures/gui/gui_alchemyfurnace.png");
    public VibrationChamberScreen(VibrationChamberMenu menu,Inventory inventory,Component title){super(menu,inventory,title);imageWidth=176;imageHeight=82;}
    @Override protected void renderBg(GuiGraphics graphics,float partial,int mouseX,int mouseY){
        graphics.flush();RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();graphics.setColor(1,1,1,1);
        graphics.blit(BACKGROUND,leftPos,topPos,0,0,imageWidth,imageHeight,256,256);
        int power=(int)(18F*menu.powerPercent());graphics.blit(BACKGROUND,leftPos+96,topPos+32+power,51,83+power,6,18-power,256,256);
        int stored=(int)(48F*menu.storedPercent());graphics.blit(ALCHEMY,leftPos+62,topPos+16+stored,200,stored,8,48-stored,256,256);
        graphics.blit(ALCHEMY,leftPos+61,topPos+12,232,0,11,56,256,256);
        int ticks=(int)(20F*menu.ticksPercent());graphics.blit(ALCHEMY,leftPos+78,topPos+30+ticks,177,ticks,15,20-ticks,256,256);
        graphics.setColor(1,1,1,1);
    }
    @Override protected void renderLabels(GuiGraphics graphics,int x,int y){}
    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float partial){
        super.render(graphics,mouseX,mouseY,partial);var tooltip=new ArrayList<Component>();
        if(isHovering(61,12,11,56,mouseX,mouseY))tooltip.add(Component.literal(menu.storedAmount()>0&&menu.storedAspect()!=null?menu.storedAspect().getName()+" x "+menu.storedAmount():"Empty"));
        else if(isHovering(78,30,15,20,mouseX,mouseY)&&menu.processingAspect()!=null){tooltip.add(Component.literal("Processing: "+menu.processingAspect().getName()));tooltip.add(Component.literal(String.format("Time Remaining: %.0fs",menu.ticksRemaining()/20F)));}
        else if(isHovering(96,32,6,18,mouseX,mouseY))tooltip.add(Component.literal(String.format("%.0f AE/t",menu.powerPerTick())));
        if(!tooltip.isEmpty())graphics.renderTooltip(font,tooltip,Optional.empty(),mouseX,mouseY);
    }
}
