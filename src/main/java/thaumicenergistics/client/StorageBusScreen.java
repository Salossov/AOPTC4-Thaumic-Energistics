package thaumicenergistics.client;

import appeng.client.gui.Icon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.StorageBusMenu;

public final class StorageBusScreen extends AbstractContainerScreen<StorageBusMenu> {
    private static final ResourceLocation TEXTURE = ThaumicEnergistics.id("textures/gui/essentia.storage.bus.png");
    private final EssentiaRendering aspects = new EssentiaRendering();
    public StorageBusScreen(StorageBusMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title);
        imageWidth = menu.hasNetworkTool() ? 246 : 210; imageHeight = 184;
        titleLabelX = 6; titleLabelY = 5;
    }
    static void tab(GuiGraphics graphics, int x, int y) {
        Icon.TAB_BUTTON_BACKGROUND.getBlitter().dest(x,y,22,22).blit(graphics);
        var wrench = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:nether_quartz_wrench"));
        graphics.renderItem(wrench.getDefaultInstance(),x+3,y+3);
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiTextures.blit(graphics, TEXTURE,leftPos,topPos,0,0,176,222);
        GuiTextures.blit(graphics, TEXTURE,leftPos+179,topPos,179,0,32,32);
        if (menu.hasNetworkTool()) GuiTextures.blit(graphics, TEXTURE,leftPos+179,topPos+93,178,93,68,68);
        if (!menu.getSlot(9).hasItem()) Icon.BACKGROUND_UPGRADE.getBlitter().dest(leftPos+187,topPos+8).blit(graphics);
        tab(graphics,leftPos+154,topPos);
        Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(leftPos-18,topPos+8,18,18).blit(graphics);
        var jar = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:void_jar"));
        graphics.renderItem(jar.getDefaultInstance(),leftPos-17,topPos+9);
        if (!menu.allowVoid()) Icon.INVALID.getBlitter().dest(leftPos-17,topPos+9).blit(graphics);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int x, int y) { graphics.drawString(font,title,titleLabelX,titleLabelY,0,false); }
    @Override protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (slot.index < 9) { var key = menu.filter(slot.index); if (key != null) aspects.drawInGui(minecraft,graphics,slot.x,slot.y,key); }
        else super.renderSlot(graphics,slot);
    }
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        super.render(graphics,x,y,partialTick); renderTooltip(graphics,x,y);
        if (isHovering(154,0,22,22,x,y)) graphics.renderTooltip(font,Component.translatable("gui.ae2.Priority"),x,y);
        if (isHovering(-18,8,18,18,x,y)) graphics.renderTooltip(font,Component.translatable(menu.allowVoid()
                ? "thaumicenergistics.tooltip.button.void.allowed" : "thaumicenergistics.tooltip.button.void.disabled"),x,y);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if ((button == 0 || button == 1) && minecraft.gameMode != null) {
            int action = isHovering(154,0,22,22,x,y) ? StorageBusMenu.PRIORITY : isHovering(-18,8,18,18,x,y) ? StorageBusMenu.VOID : -1;
            if (action >= 0) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action); return true; }
        }
        return super.mouseClicked(x,y,button);
    }
}
