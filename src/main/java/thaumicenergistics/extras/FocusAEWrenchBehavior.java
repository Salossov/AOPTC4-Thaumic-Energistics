package thaumicenergistics.extras;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.wands.FocusBehavior;

/**
 * Core casting hook for the right-click path (the original onFocusRightClick).
 * The core FocusCasting pipeline owns the vis reservation, refund on failure
 * and the (1000+49)/50 tick cooldown; this behavior runs the original
 * successful block path, coordinates shared cooldown with left-click, and
 * reports whether it happened.
 */
public final class FocusAEWrenchBehavior implements FocusBehavior {
    public static final FocusAEWrenchBehavior INSTANCE = new FocusAEWrenchBehavior();

    @Override
    public boolean cast(ServerPlayer player, ItemStack wand, ItemStack focus) {
        if (FocusAEWrenchInteraction.isOnCooldown(player)) {
            return false;
        }
        boolean success = FocusAEWrenchInteraction.castRightClick(player, wand, focus);
        if (success && focus.getItem() instanceof FocusAEWrenchItem focusItem) {
            FocusAEWrenchInteraction.applyCooldown(player, focusItem.getActivationCooldown(focus));
        }
        return success;
    }

    @Override
    public boolean begin(ServerPlayer player, ItemStack wand, ItemStack focus, InteractionHand hand) {
        return !FocusAEWrenchInteraction.isOnCooldown(player);
    }

    private FocusAEWrenchBehavior() {}
}
