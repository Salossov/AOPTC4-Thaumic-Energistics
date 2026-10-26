package thaumicenergistics.distillation;

import appeng.api.stacks.GenericStack;
import appeng.client.gui.Icon;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.storage.EssentiaKey;

/**
 * Client screen for Distillation Pattern Encoder.
 * - GUI width 176, height 234.
 * - Spinning thaumometer background animation behind source item slot.
 * - 6 source aspect widgets and 1 selected aspect widget with aspect icon rendering and amount decoration.
 * - Encode button with enabled/disabled visual states and tooltips.
 */
public final class DistillationPatternEncoderScreen extends AbstractContainerScreen<DistillationPatternEncoderMenu> {
    private static final thaumicenergistics.client.EssentiaRendering ASPECT_RENDERER =
            new thaumicenergistics.client.EssentiaRendering();
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "textures/gui/distillation.encoder.png");
    private static final ResourceLocation THAUMOMETER_ID =
            ResourceLocation.fromNamespaceAndPath("thaumcraft", "thaumometer");

    private final ItemStack thaumometerStack;

    public DistillationPatternEncoderScreen(DistillationPatternEncoderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 234;
        titleLabelX = 6;
        titleLabelY = 6;

        var thaumometerItem = BuiltInRegistries.ITEM.get(THAUMOMETER_ID);
        this.thaumometerStack = thaumometerItem != null ? new ItemStack(thaumometerItem) : ItemStack.EMPTY;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        // Draw main GUI background
        graphics.blit(TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        // Rotating thaumometer animation when source slot is empty
        if (menu.getSlot(DistillationPatternEncoderMenu.SLOT_SOURCE_ITEM).getItem().isEmpty() && !thaumometerStack.isEmpty()) {
            float thPosX = leftPos + 15.0F + 8.0F;
            float thPosY = topPos + 69.0F + 8.0F;
            float thRotation = (float) (System.currentTimeMillis() % 36000L) * 0.02F;
            float thScaleOffset = (float) Math.sin(thRotation * 0.15F) * 0.1F;

            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(thPosX, thPosY, 0.0F);
            pose.scale(2.8F + thScaleOffset, 2.8F + thScaleOffset, 1.0F);
            pose.mulPose(Axis.ZP.rotationDegrees(thRotation));
            graphics.renderItem(thaumometerStack, -8, -8);
            pose.popPose();
        }

        // Render Encode Button at (146, 94)
        int btnX = leftPos + 146;
        int btnY = topPos + 94;
        // Modern AE2's toolbar background is 18x20; this legacy control is 16x16.
        Icon.TOOLBAR_BUTTON_BACKGROUND.getBlitter().dest(btnX, btnY, 16, 16).blit(graphics);
        Icon.ARROW_DOWN.getBlitter().dest(btnX, btnY, 16, 16).blit(graphics);

        if (!menu.canEncode()) {
            // Disabled overlay
            graphics.fill(btnX, btnY, btnX + 16, btnY + 16, 0x80555555);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        thaumicenergistics.client.GuiTitle.draw(graphics, font, title, titleLabelX, titleLabelY, imageWidth - titleLabelX - 6, 0x404040, mouseX, mouseY);
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (slot.index >= DistillationPatternEncoderMenu.SLOT_SOURCE_ASPECTS_START
                && slot.index <= DistillationPatternEncoderMenu.SLOT_SELECTED_ASPECT) {
            ItemStack stack = slot.getItem();
            if (!stack.isEmpty()) {
                var generic = GenericStack.unwrapItemStack(stack);
                if (generic != null && generic.what() instanceof EssentiaKey essentiaKey) {
                    Aspect aspect = essentiaKey.aspect();
                    long amount = generic.amount();
                    if (aspect == null || aspect.getTag().equals("unknown")) {
                        // Render unknown placeholder '?'
                        graphics.drawString(font, "?", slot.x + 5, slot.y + 4, 0xAAAAAA, false);
                    } else {
                        // Use the same flushed, native-color rendering as terminals and filters.
                        ASPECT_RENDERER.drawInGui(minecraft, graphics, slot.x, slot.y, essentiaKey);

                        if (amount > 1) {
                            String amtStr = String.valueOf(amount);
                            var pose = graphics.pose();
                            pose.pushPose();
                            pose.translate(0, 0, 200);
                            graphics.drawString(font, amtStr, slot.x + 17 - font.width(amtStr), slot.y + 9, 0xFFFFFF, true);
                            pose.popPose();
                        }
                    }
                }
            }
        } else {
            super.renderSlot(graphics, slot);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        // Encode button tooltip
        if (isHovering(146, 94, 16, 16, mouseX, mouseY)) {
            List<Component> tooltip = new ArrayList<>();
            tooltip.add(Component.translatable("gui.tooltips.ae2.Encode"));
            tooltip.add(Component.translatable("gui.tooltips.ae2.EncodeDescription").withStyle(ChatFormatting.GRAY));
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
            return;
        }

        // Aspect widgets tooltips
        for (int i = DistillationPatternEncoderMenu.SLOT_SOURCE_ASPECTS_START;
             i <= DistillationPatternEncoderMenu.SLOT_SELECTED_ASPECT; i++) {
            Slot slot = menu.getSlot(i);
            if (isHovering(slot.x, slot.y, 16, 16, mouseX, mouseY)) {
                ItemStack stack = slot.getItem();
                if (!stack.isEmpty()) {
                    var generic = GenericStack.unwrapItemStack(stack);
                    if (generic != null && generic.what() instanceof EssentiaKey essentiaKey) {
                        Aspect aspect = essentiaKey.aspect();
                        long amount = generic.amount();
                        if (aspect == null || aspect.getTag().equals("unknown")) {
                            graphics.renderTooltip(font, Component.translatable("tc.aspect.unknown"), mouseX, mouseY);
                        } else {
                            List<Component> tooltip = new ArrayList<>();
                            tooltip.add(Component.literal(aspect.getName() + " x " + amount).withStyle(ChatFormatting.AQUA));
                            if (!aspect.isPrimal() && aspect.getComponents().size() == 2) {
                                tooltip.add(Component.literal(aspect.getComponents().get(0).getName() + " + "
                                        + aspect.getComponents().get(1).getName()).withStyle(ChatFormatting.DARK_GRAY));
                            }
                            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
                        }
                    }
                }
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHovering(146, 94, 16, 16, mouseX, mouseY)) {
            if (menu.canEncode()) {
                if (minecraft != null && minecraft.getSoundManager() != null) {
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                }
                BlockPos pos = menu.getBlockEntity() != null ? menu.getBlockEntity().getBlockPos() : BlockPos.ZERO;
                PacketDistributor.sendToServer(new DistillationPatternEncoderActionPayload(
                        menu.containerId, pos, DistillationPatternEncoderActionPayload.ACTION_ENCODE, 0));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
