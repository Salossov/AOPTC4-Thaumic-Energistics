package thaumicenergistics.extras;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.wands.FocusBehavior;

/**
 * Core casting hook for the right-click path (the original onFocusRightClick).
 * The core FocusCasting pipeline owns the vis reservation, refund on failure
 * and the (1000+49)/50 tick cooldown; this behavior only runs the original
 * successful block path and reports whether it happened.
 */
public final class FocusAEWrenchBehavior implements FocusBehavior {
    static final FocusAEWrenchBehavior INSTANCE = new FocusAEWrenchBehavior();

    @Override
    public boolean cast(ServerPlayer player, ItemStack wand, ItemStack focus) {
        return FocusAEWrenchInteraction.castRightClick(player, wand, focus);
    }

    @Override
    public boolean begin(ServerPlayer player, ItemStack wand, ItemStack focus, InteractionHand hand) {
        return true;
    }

    private FocusAEWrenchBehavior() {}
}
