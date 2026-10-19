package thaumicenergistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.EssentiaBusMenu;
import appeng.client.gui.Icon;

/** Original 1.1.3.0 texture and slot coordinates. */
public final class EssentiaBusScreen extends AbstractContainerScreen<EssentiaBusMenu> {
    private static final ResourceLocation BACKGROUND = ThaumicEnergistics.id("textures/gui/essentia.io.bus.png");

    public EssentiaBusScreen(EssentiaBusMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = menu.hasNetworkTool() ? 246 : 211;
        imageHeight = 184;
        titleLabelX = 6; titleLabelY = 5;
        inventoryLabelY = 90;
    }

    private int voidY() { return menu.hasRedstoneCard() ? 20 : 2; }
    private void button(GuiGraphics graphics, int y, Icon icon) {
        Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(leftPos - 18, topPos + y).blit(graphics);
        icon.getBlitter().dest(leftPos - 18, topPos + y).blit(graphics);
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(BACKGROUND, leftPos, topPos, 0, 0, 176, 184);
        graphics.blit(BACKGROUND, leftPos + 176, topPos, 176, 0, 35, 86);
        if (menu.hasNetworkTool()) graphics.blit(BACKGROUND, leftPos + 176, topPos + 96, 176, 96, 70, 64);
        for (int i = 0; i < 9; i++) {
            int x = leftPos + 62 + i % 3 * 18, y = topPos + 22 + i / 3 * 18;
            // Enabled slots have the same dark inset as the center slot in the release texture.
            if (menu.filterEnabled(i)) graphics.blit(BACKGROUND, x - 1, y - 1, 79, 39, 18, 18);
        }
        if (menu.hasRedstoneCard()) {
            var icon = switch (menu.redstoneMode()) {
                case LOW_SIGNAL -> Icon.REDSTONE_LOW; case HIGH_SIGNAL -> Icon.REDSTONE_HIGH;
                case SIGNAL_PULSE -> Icon.REDSTONE_PULSE; case IGNORE -> Icon.REDSTONE_IGNORE;
            };
            button(graphics, 2, icon);
        }
        if (!menu.importing()) {
            Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(leftPos - 19, topPos + voidY()).blit(graphics);
            var jar = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:void_jar"));
            graphics.renderItem(jar.getDefaultInstance(), leftPos - 19, topPos + voidY());
            if (!menu.allowVoid()) Icon.INVALID.getBlitter().dest(leftPos - 19, topPos + voidY()).blit(graphics);
        }
    }

    @Override protected void renderSlot(GuiGraphics graphics, net.minecraft.world.inventory.Slot slot) {
        if (slot.index < 9) {
            var key = menu.filter(slot.index);
            if (key != null && slot.isActive()) new EssentiaRendering().drawInGui(minecraft, graphics, slot.x, slot.y, key);
        } else super.renderSlot(graphics, slot);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (menu.hasRedstoneCard() && isHovering(-18, 2, 16, 16, mouseX, mouseY)) {
            String mode = switch (menu.redstoneMode()) {
                case IGNORE -> "AlwaysActive"; case HIGH_SIGNAL -> "ActiveWithSignal";
                case LOW_SIGNAL -> "ActiveWithoutSignal"; case SIGNAL_PULSE -> "ActiveOnPulse";
            };
            graphics.renderTooltip(font, Component.translatable("gui.tooltips.ae2." + mode), mouseX, mouseY);
        }
        if (!menu.importing() && isHovering(-19, voidY(), 16, 16, mouseX, mouseY))
            graphics.renderTooltip(font, Component.translatable(menu.allowVoid() ? "thaumicenergistics.tooltip.button.void.allowed" : "thaumicenergistics.tooltip.button.void.disabled"), mouseX, mouseY);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if (button == 0 && minecraft.gameMode != null) {
            int action = menu.hasRedstoneCard() && isHovering(-18, 2, 16, 16, x, y) ? EssentiaBusMenu.REDSTONE_BUTTON
                    : !menu.importing() && isHovering(-19, voidY(), 16, 16, x, y) ? EssentiaBusMenu.VOID_BUTTON : -1;
            if (action >= 0) { minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action); return true; }
        }
        return super.mouseClicked(x, y, button);
    }
}
