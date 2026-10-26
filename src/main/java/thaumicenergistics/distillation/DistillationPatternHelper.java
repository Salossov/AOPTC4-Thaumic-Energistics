package thaumicenergistics.distillation;

import appeng.api.crafting.IPatternDetails;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEItems;
import java.util.List;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumicenergistics.storage.EssentiaKey;

/**
 * Handles encoding and decoding of native AE2 processing patterns for essentia distillation.
 * Processing pattern: 1 source AEItemKey (amount 1) -> 1 EssentiaKey (positive amount).
 */
public final class DistillationPatternHelper {
    public record DecodedPattern(ItemStack sourceItem, Aspect aspect, int amount, boolean valid) {
        public static final DecodedPattern INVALID = new DecodedPattern(ItemStack.EMPTY, null, 0, false);
    }

    private DistillationPatternHelper() {}

    public static boolean isValidPattern(ItemStack stack) {
        return !stack.isEmpty() && PatternDetailsHelper.isEncodedPattern(stack);
    }

    public static boolean isBlankPattern(ItemStack stack) {
        return !stack.isEmpty() && AEItems.BLANK_PATTERN.is(stack);
    }

    public static ItemStack encodePattern(ItemStack sourceItem, Aspect selectedAspect, int amount) {
        if (sourceItem.isEmpty() || selectedAspect == null || amount <= 0) {
            return ItemStack.EMPTY;
        }
        var inputStack = new GenericStack(AEItemKey.of(sourceItem.copyWithCount(1)), 1);
        var outputStack = new GenericStack(EssentiaKey.of(selectedAspect), amount);
        return PatternDetailsHelper.encodeProcessingPattern(List.of(inputStack), List.of(outputStack));
    }

    public static DecodedPattern decodePattern(ItemStack patternStack, Level level) {
        if (patternStack.isEmpty() || !PatternDetailsHelper.isEncodedPattern(patternStack)) {
            return DecodedPattern.INVALID;
        }
        IPatternDetails details = PatternDetailsHelper.decodePattern(patternStack, level);
        if (details == null) {
            return DecodedPattern.INVALID;
        }
        var inputs = details.getInputs();
        if (inputs == null || inputs.length != 1) {
            return DecodedPattern.INVALID;
        }
        var possibleInputs = inputs[0].getPossibleInputs();
        if (possibleInputs == null || possibleInputs.length != 1) {
            return DecodedPattern.INVALID;
        }
        var inGeneric = possibleInputs[0];
        if (!(inGeneric.what() instanceof AEItemKey itemKey) || inGeneric.amount() != 1 || inputs[0].getMultiplier() != 1) {
            return DecodedPattern.INVALID;
        }

        var outputs = details.getOutputs();
        if (outputs == null || outputs.size() != 1) {
            return DecodedPattern.INVALID;
        }
        var outGeneric = outputs.get(0);
        if (!(outGeneric.what() instanceof EssentiaKey essentiaKey) || outGeneric.amount() <= 0 || outGeneric.amount() > Integer.MAX_VALUE) {
            return DecodedPattern.INVALID;
        }
        Aspect aspect = essentiaKey.aspect();
        if (aspect == null) {
            return DecodedPattern.INVALID;
        }
        return new DecodedPattern(itemKey.toStack(1).copy(), aspect, (int) outGeneric.amount(), true);
    }
}
