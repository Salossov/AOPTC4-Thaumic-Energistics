package thaumicenergistics.client;

import appeng.client.gui.Icon;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.CellWorkbenchMenu;

/** Original 176x251 texture, 9x7 partition grid and two side buttons. */
public final class CellWorkbenchScreen extends AbstractContainerScreen<CellWorkbenchMenu> {
    private static final ResourceLocation TEXTURE = ThaumicEnergistics.id("textures/gui/essentia.cell.workbench.png");
    private final EssentiaRendering aspects = new EssentiaRendering();
    public CellWorkbenchScreen(CellWorkbenchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176; imageHeight = 251;
        titleLabelX = 6; titleLabelY = 6;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        GuiTextures.blit(graphics, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        if (!menu.getSlot(CellWorkbenchMenu.CELL_SLOT).hasItem())
            Icon.BACKGROUND_STORAGE_CELL.getBlitter().dest(leftPos + 152, topPos + 8).blit(graphics);
        Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(leftPos - 18, topPos + 8).blit(graphics);
        Icon.CLEAR.getBlitter().dest(leftPos - 18, topPos + 8).blit(graphics);
        Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(leftPos - 18, topPos + 28).blit(graphics);
        var wrench = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("ae2:nether_quartz_wrench"));
        graphics.renderItem(wrench.getDefaultInstance(), leftPos - 18, topPos + 28);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int x, int y) { graphics.drawString(font, title, titleLabelX, titleLabelY, 0, false); }
    @Override protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (slot.index < CellWorkbenchMenu.FILTER_COUNT) {
            var key = menu.filter(slot.index);
            if (key != null) aspects.drawInGui(minecraft, graphics, slot.x, slot.y, key);
        } else super.renderSlot(graphics, slot);
    }
    @Override public void render(GuiGraphics graphics, int x, int y, float partialTick) {
        super.render(graphics, x, y, partialTick);
        renderTooltip(graphics, x, y);
        if (isHovering(-18, 8, 16, 16, x, y)) graphics.renderTooltip(font, Component.translatable("gui.tooltips.ae2.ClearSettings"), x, y);
        if (isHovering(-18, 28, 16, 16, x, y)) graphics.renderTooltip(font, Component.translatable("gui.tooltips.ae2.PartitionStorageHint"), x, y);
    }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if ((button == 0 || button == 1) && minecraft.gameMode != null) {
            int action = isHovering(-18, 8, 16, 16, x, y) ? CellWorkbenchMenu.CLEAR
                    : isHovering(-18, 28, 16, 16, x, y) ? CellWorkbenchMenu.PARTITION_CONTENTS : -1;
            if (action >= 0) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action); return true; }
        }
        return super.mouseClicked(x, y, button);
    }
}
