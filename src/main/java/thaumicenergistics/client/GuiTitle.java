package thaumicenergistics.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/** Localized compact headers; the original title is available through a native tooltip. */
public final class GuiTitle {
    private GuiTitle() {}
    public static Component displayTitle(Font font, Component title, int available) {
        if (font.width(title) <= available) return title;
        Component display = title;
        if (title.getContents() instanceof TranslatableContents translated) {
            String key = translated.getKey() + ".short";
            if (I18n.exists(key)) display = Component.translatable(key, translated.getArgs()).setStyle(title.getStyle());
        }
        if (font.width(display) <= available) return display;
        String ellipsis = "…";
        String text = font.plainSubstrByWidth(display.getString(), Math.max(0, available - font.width(ellipsis)));
        return Component.literal(text + ellipsis).setStyle(title.getStyle());
    }
    public static void draw(GuiGraphics graphics, Font font, Component title, int x, int y, int available, int color, int mouseX, int mouseY) {
        graphics.drawString(font, displayTitle(font, title, available), x, y, color, false);
        if (font.width(title) <= available) return;
        // Container labels use a translated pose; mouse coordinates stay in screen space.
        var pose = graphics.pose().last().pose();
        var start = pose.transformPosition(x, y, 0, new org.joml.Vector3f());
        var end = pose.transformPosition(x + available, y + font.lineHeight, 0, new org.joml.Vector3f());
        var screen = Minecraft.getInstance().screen;
        if (screen != null && mouseX >= start.x && mouseX < end.x && mouseY >= start.y && mouseY < end.y)
            screen.setTooltipForNextRenderPass(title);
    }
}
