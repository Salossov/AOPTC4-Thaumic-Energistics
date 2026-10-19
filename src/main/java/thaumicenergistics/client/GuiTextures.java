package thaumicenergistics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** Release GUI textures have transparent corners; restore alpha blending after batched item/font draws. */
final class GuiTextures {
    private GuiTextures() {}
    static void blit(GuiGraphics graphics, ResourceLocation texture, int x, int y, int u, int v, int width, int height) {
        graphics.flush(); RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        graphics.setColor(1,1,1,1);
        RenderSystem.disableDepthTest();
        graphics.blit(texture,x,y,u,v,width,height);
        RenderSystem.enableDepthTest();
    }
}
