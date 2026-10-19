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

public final class StoragePriorityScreen extends AbstractContainerScreen<StoragePriorityMenu> {
    private static final ResourceLocation TEXTURE = ThaumicEnergistics.id("textures/gui/priority.png");
    private EditBox amount;
    private int serverValue;
    private boolean receiving;
    public StoragePriorityScreen(StoragePriorityMenu menu, Inventory inventory, Component title) {
        super(menu,inventory,title); imageWidth = 176; imageHeight = 107; titleLabelX = 6; titleLabelY = 6;
    }
    @Override protected void init() {
        super.init();
        int[] widths = {25,35,45}, deltas = {1,10,100,-1,-10,-100};
        for (int row = 0; row < 2; row++) {
            int x = leftPos+35;
            for (int col = 0; col < 3; col++) {
                final int action = row*3+col+1; int delta = deltas[action-1];
                addRenderableWidget(Button.builder(Component.literal((delta > 0 ? "+" : "") + delta),button -> sendButton(action))
                        .bounds(x,topPos+(row == 0 ? 30 : 70),widths[col],20).build());
                x += widths[col]+5;
            }
        }
        amount = new EditBox(font,leftPos+61,topPos+57,80,10,Component.translatable("gui.ae2.Priority"));
        amount.setBordered(false); amount.setTextColor(0xffffffff); amount.setMaxLength(11);
        // Preserve the original eight-character typing limit while allowing full signed
        // values from saved settings, and allow backspace at the limit instead of freezing.
        amount.setFilter(value -> receiving || (value.length() <= 8 && value.matches("-?[0-9]*")));
        updateFromServer();
        amount.setResponder(value -> {
            if (receiving || value.equals("-")) return;
            try { PacketDistributor.sendToServer(new PriorityPayload(menu.containerId,value.isEmpty() ? 0 : Integer.parseInt(value))); }
            catch (NumberFormatException ignored) { }
        });
        addRenderableWidget(amount); setInitialFocus(amount);
    }
    private void sendButton(int action) { if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId,action); }
    private void updateFromServer() {
        serverValue = menu.priority(); receiving = true;
        try { amount.setValue(Integer.toString(serverValue)); } finally { receiving = false; }
    }
    @Override protected void containerTick() { if (amount != null && serverValue != menu.priority()) updateFromServer(); }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int x, int y) {
        graphics.blit(TEXTURE,leftPos,topPos,0,0,imageWidth,imageHeight);
        StorageBusScreen.tab(graphics,leftPos+154,topPos);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int x, int y) { graphics.drawString(font,title,titleLabelX,titleLabelY,0,false); }
    @Override public boolean mouseClicked(double x, double y, int button) {
        if ((button == 0 || button == 1) && isHovering(154,0,22,22,x,y)) { sendButton(0); return true; }
        return super.mouseClicked(x,y,button);
    }
}
