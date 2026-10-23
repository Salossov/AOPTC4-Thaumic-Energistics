package thaumicenergistics.extras;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.Aspects;
import thaumcraft.api.wands.FocusUpgradeType;
import thaumcraft.api.wands.ItemFocusBasic;

/**
 * The original ItemFocusAEWrench (release 1.1.3.0, the sole reference): a wand
 * focus that behaves like an AE2 wrench. Cast cost FIRE 10 + AIR 10 centivis,
 * activation cooldown 1000ms, Potentia (Aspect.ENERGY) color, no upgrades, and
 * the enabled/disabled name branch. Left/right block behavior lives in
 * {@link FocusAEWrenchBehavior} (right-click, via the core casting hooks) and
 * {@link ExtrasLeftClickHandler} (left-click, via a server-authoritative event).
 */
public final class FocusAEWrenchItem extends ItemFocusBasic {
    /** Original castCost: Aspect.FIRE 10 + Aspect.AIR 10, centivis per the core WandApi contract. */
    public static Map<Aspect, Integer> castCost() {
        Map<Aspect, Integer> cost = new LinkedHashMap<>();
        cost.put(Aspects.FIRE, 10);
        cost.put(Aspects.AIR, 10);
        return java.util.Collections.unmodifiableMap(cost);
    }

    @Override
    public Map<Aspect, Integer> getVisCost(ItemStack stack) {
        return castCost();
    }

    @Override
    public int getActivationCooldown(ItemStack stack) {
        return 1000;
    }

    @Override
    public int getFocusColor(ItemStack stack) {
        return Aspects.ENERGY.getColor();
    }

    @Override
    public List<FocusUpgradeType> getPossibleUpgradesByRank(ItemStack stack, int rank) {
        return List.of();
    }

    @Override
    public Component getName(ItemStack stack) {
        try {
            if (ExtrasConfig.wrenchFocus()) {
                return Component.translatable("thaumicenergistics.item.focus.aewrench.name");
            }
        } catch (Exception disabled) {
            // Original swallowed feature lookup failures into the disabled name.
        }
        return Component.translatable("thaumicenergistics.item.focus.aewrench.disabled.name");
    }
}
