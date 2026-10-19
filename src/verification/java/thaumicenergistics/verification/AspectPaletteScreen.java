package thaumicenergistics.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.Aspects;
import thaumicenergistics.client.EssentiaRendering;
import thaumicenergistics.storage.EssentiaKey;

/** Verification-only checkerboard behind the actual production AEKey renderer. */
public final class AspectPaletteScreen extends Screen {
    private static final List<Aspect> ASPECTS=List.of(Aspects.AIR,Aspects.EARTH,Aspects.FIRE,Aspects.WATER,Aspects.ORDER,Aspects.ENTROPY,Aspects.MAGIC);
    public AspectPaletteScreen(){super(Component.literal("Aspect color and alpha verification"));}
    private int left(){return (width-320)/2;}
    private int top(){return (height-128)/2;}
    public List<Map<String,Object>> samples() {
        var result=new ArrayList<Map<String,Object>>();
        for(int i=0;i<ASPECTS.size();i++) {
            var a=ASPECTS.get(i);
            result.add(Map.of("tag",a.getTag(),"color",a.getColor(),"texture",a.texture(),"x",left()+16+i*40,"y",top()+48));
        }
        return result;
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        super.render(g,mouseX,mouseY,partialTick);
        g.fill(0,0,width,height,0xff292f36);g.fill(left(),top(),left()+320,top()+128,0xffbcbec0);
        g.drawString(font,title,left()+8,top()+8,0xff202020,false);
        for(int i=0;i<ASPECTS.size();i++) {
            int x=left()+16+i*40,y=top()+48;
            for(int yy=0;yy<16;yy+=4)for(int xx=0;xx<16;xx+=4)
                g.fill(x+xx,y+yy,x+xx+4,y+yy+4,((xx/4+yy/4)%2==0)?0xffb84b36:0xff326eab);
            new EssentiaRendering().drawInGui(minecraft,g,x,y,EssentiaKey.of(ASPECTS.get(i)));
            g.drawWordWrap(font,Component.literal(ASPECTS.get(i).getTag()),x-4,y+24,36,0xff202020);
        }
    }
    @Override public boolean isPauseScreen(){return false;}
}
