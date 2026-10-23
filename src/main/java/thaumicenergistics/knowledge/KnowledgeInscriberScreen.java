package thaumicenergistics.knowledge;

import appeng.client.gui.Icon;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumicenergistics.ThaumicEnergistics;

/**
 * Screen for Knowledge Inscriber.
 * Size 210x244, research background at (80, 106), Save/Delete button at (141, 109),
 * Clear button at (80, 89).
 */
public class KnowledgeInscriberScreen extends AbstractContainerScreen<KnowledgeInscriberMenu> {
    private static final ResourceLocation GUI_TEXTURE = ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "textures/gui/knowledge.inscriber.png");
    private static final ResourceLocation RESEARCH_BG = ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "textures/research/research.background.png");

    private static final int BUTTON_SAVE_X = 141;
    private static final int BUTTON_SAVE_Y = 109;
    private static final int BUTTON_CLEAR_X = 80;
    private static final int BUTTON_CLEAR_Y = 89;

    private KnowledgeInscriberMenu.CoreSaveState saveState = KnowledgeInscriberMenu.CoreSaveState.Disabled_MissingCore;

    public KnowledgeInscriberScreen(KnowledgeInscriberMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageWidth = 210;
        this.imageHeight = 244;
        this.titleLabelX = 6;
        this.titleLabelY = 6;
        this.inventoryLabelY = 150;
    }

    @Override
    protected void init() {
        super.init();
        PacketDistributor.sendToServer(new KnowledgeInscriberActionPayload(this.menu.containerId, KnowledgeInscriberActionPayload.ACTION_FULL_UPDATE, 0));
    }

    public void onReceiveSaveState(KnowledgeInscriberMenu.CoreSaveState state, boolean justSaved) {
        this.saveState = state != null ? state : KnowledgeInscriberMenu.CoreSaveState.Disabled_MissingCore;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Render pulsating research background behind the result slot at (80, 106), size 35x20
        long slowTime = System.currentTimeMillis() / 100L;
        float redBounce = Math.abs(1.0F - (((slowTime % 50) / 50.0F) * 2.0F));
        float greenBounce = 1.0F - redBounce;

        RenderSystem.enableBlend();
        graphics.setColor(redBounce, greenBounce, 1.0F, 1.0F);
        int xpos = (int) (slowTime % 10);
        graphics.blit(RESEARCH_BG, leftPos + 80, topPos + 106, 55 - xpos, 25, 35, 20);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        // Render main GUI texture
        graphics.blit(GUI_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight);

        // Render Clear button at (80, 89)
        Icon.CLEAR.getBlitter().dest(leftPos + BUTTON_CLEAR_X, topPos + BUTTON_CLEAR_Y, 8, 8).blit(graphics);

        // Render Save/Delete button at (141, 109)
        Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(leftPos + BUTTON_SAVE_X, topPos + BUTTON_SAVE_Y, 16, 16).blit(graphics);
        if (saveState == KnowledgeInscriberMenu.CoreSaveState.Enabled_Save) {
            // Save icon from AE2 states texture
            graphics.blit(Icon.TEXTURE, leftPos + BUTTON_SAVE_X, topPos + BUTTON_SAVE_Y, 0, 176, 16, 16);
        } else if (saveState == KnowledgeInscriberMenu.CoreSaveState.Enabled_Delete) {
            // Delete icon from AE2 states texture
            graphics.blit(Icon.TEXTURE, leftPos + BUTTON_SAVE_X, topPos + BUTTON_SAVE_Y, 0, 192, 16, 16);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        // Clear button tooltip
        if (isHovering(BUTTON_CLEAR_X, BUTTON_CLEAR_Y, 8, 8, mouseX, mouseY)) {
            graphics.renderTooltip(this.font, Component.translatable("gui.tooltips.ae2.ClearSettings"), mouseX, mouseY);
        }

        // Save/Delete button tooltip
        if (isHovering(BUTTON_SAVE_X, BUTTON_SAVE_Y, 16, 16, mouseX, mouseY)) {
            Component tooltip = switch (saveState) {
                case Disabled_InvalidRecipe -> Component.translatable("thaumicenergistics.tooltip.button.inscriber.invalid");
                case Disabled_CoreFull -> Component.translatable("thaumicenergistics.tooltip.button.inscriber.full");
                case Disabled_MissingCore -> Component.translatable("thaumicenergistics.tooltip.button.inscriber.missing");
                case Enabled_Save -> Component.translatable("thaumicenergistics.tooltip.button.inscriber.save");
                case Enabled_Delete -> Component.translatable("thaumicenergistics.tooltip.button.inscriber.delete");
            };
            graphics.renderTooltip(this.font, tooltip, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isHovering(BUTTON_CLEAR_X, BUTTON_CLEAR_Y, 8, 8, mouseX, mouseY)) {
                if (minecraft != null && minecraft.getSoundManager() != null) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                PacketDistributor.sendToServer(new KnowledgeInscriberActionPayload(this.menu.containerId, KnowledgeInscriberActionPayload.ACTION_CLEAR, 0));
                return true;
            }

            if (isHovering(BUTTON_SAVE_X, BUTTON_SAVE_Y, 16, 16, mouseX, mouseY)) {
                if (saveState == KnowledgeInscriberMenu.CoreSaveState.Enabled_Save || saveState == KnowledgeInscriberMenu.CoreSaveState.Enabled_Delete) {
                    if (minecraft != null && minecraft.getSoundManager() != null) {
                        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    PacketDistributor.sendToServer(new KnowledgeInscriberActionPayload(this.menu.containerId, KnowledgeInscriberActionPayload.ACTION_SAVE_DELETE, 0));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
