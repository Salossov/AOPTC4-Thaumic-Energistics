package thaumicenergistics.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.menu.StoragePriorityMenu;
import thaumicenergistics.network.PriorityPayload;

/**
 * Storage Priority Screen.
 * Renders priority GUI with signed adjustment buttons (+1, +10, +100, -1, -10, -100),
 * an edit box for numeric input, and a return tab to switch back to the storage bus.
 */
public final class StoragePriorityScreen extends AbstractContainerScreen<StoragePriorityMenu> {
    private static final ResourceLocation TEXTURE = ThaumicEnergistics.id("textures/gui/priority.png");
    private EditBox amount;
    private int serverValue;
    private boolean receiving;

    public StoragePriorityScreen(StoragePriorityMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 107;
        titleLabelX = 6;
        titleLabelY = 6;
    }

    @Override
    protected void init() {
        super.init();
        int[] widths = {25, 35, 45};
        int[] deltas = {1, 10, 100, -1, -10, -100};
        for (int row = 0; row < 2; row++) {
            int x = leftPos + 35;
            for (int col = 0; col < 3; col++) {
                final int action = row * 3 + col + 1;
                int delta = deltas[action - 1];
                addRenderableWidget(Button.builder(Component.literal((delta > 0 ? "+" : "") + delta), button -> sendButton(action))
                        .bounds(x, topPos + (row == 0 ? 30 : 70), widths[col], 20).build());
                x += widths[col] + 5;
            }
        }

        amount = new EditBox(font, leftPos + 61, topPos + 57, 80, 10, Component.translatable("gui.ae2.Priority"));
        amount.setBordered(false);
        amount.setTextColor(0xffffffff);
        amount.setMaxLength(11);
        amount.setFilter(value -> receiving || (value.matches("-?[0-9]*") && (value.length() <= 8 || (amount != null && value.length() < amount.getValue().length()))));
        updateFromServer();
        amount.setResponder(value -> {
            if (receiving || value.equals("-")) return;
            try {
                int parsed = value.isEmpty() ? 0 : Integer.parseInt(value);
                serverValue = parsed;
                PacketDistributor.sendToServer(new PriorityPayload(menu.containerId, parsed));
            } catch (NumberFormatException ignored) { }
        });
        addRenderableWidget(amount);
        setInitialFocus(amount);
    }

    private void sendButton(int action) {
        if (minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, action);
        }
    }

    private void updateFromServer() {
        serverValue = menu.priority();
        receiving = true;
        try {
            amount.setValue(Integer.toString(serverValue));
        } finally {
            receiving = false;
        }
    }

    @Override
    protected void containerTick() {
        if (amount != null && serverValue != menu.priority()) {
            updateFromServer();
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (amount != null && amount.isFocused() && key != 256) {
            if (amount.keyPressed(key, scan, modifiers)) return true;
            if (minecraft.options.keyInventory.matches(key, scan)) return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (isHovering(154, 0, 22, 22, mouseX, mouseY)) {
            graphics.renderTooltip(font, Component.translatable("gui.ae2.Priority"), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int x, int y) {
        GuiTextures.blit(graphics, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);
        StorageBusScreen.tab(graphics, leftPos + 154, topPos);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int x, int y) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0, false);
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if ((button == 0 || button == 1) && isHovering(154, 0, 22, 22, x, y)) {
            sendButton(0);
            return true;
        }
        return super.mouseClicked(x, y, button);
    }
}
