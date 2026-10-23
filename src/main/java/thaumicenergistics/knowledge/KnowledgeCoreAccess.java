package thaumicenergistics.knowledge;

import appeng.api.crafting.IPatternDetails;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;

/**
 * Public, safe access layer for reading patterns from a Knowledge Core.
 * Intended for the Arcane Assembler and external crafting integrations.
 */
public final class KnowledgeCoreAccess {
    private KnowledgeCoreAccess() {}

    /**
     * Checks if the stack is a valid Knowledge Core item.
     */
    public static boolean isKnowledgeCore(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof ItemKnowledgeCore;
    }

    /**
     * Reads all valid patterns stored in the knowledge core as an immutable list.
     */
    public static List<ArcaneCraftingPattern> getPatterns(ItemStack kCore, HolderLookup.Provider registries) {
        if (!isKnowledgeCore(kCore) || registries == null) {
            return Collections.emptyList();
        }
        HandlerKnowledgeCore handler = new HandlerKnowledgeCore(kCore, registries);
        return handler.getPatterns();
    }

    /**
     * Finds a stored pattern producing the given result stack, or null if none matches.
     * Matches exact item, count, and components.
     */
    public static ArcaneCraftingPattern getPatternForItem(ItemStack kCore, ItemStack result, HolderLookup.Provider registries) {
        if (!isKnowledgeCore(kCore) || result == null || result.isEmpty() || registries == null) {
            return null;
        }
        HandlerKnowledgeCore handler = new HandlerKnowledgeCore(kCore, registries);
        return handler.getPatternForItem(result);
    }

    /**
     * Checks if the knowledge core stores a pattern for the given result.
     */
    public static boolean hasPatternFor(ItemStack kCore, ItemStack result, HolderLookup.Provider registries) {
        return getPatternForItem(kCore, result, registries) != null;
    }

    /**
     * Returns the pattern as an AE2 {@link IPatternDetails} adapter.
     */
    public static IPatternDetails asPatternDetails(ArcaneCraftingPattern pattern) {
        return pattern;
    }

    /**
     * Returns all patterns in the core adapted to AE2 {@link IPatternDetails}.
     */
    public static List<IPatternDetails> getPatternDetails(ItemStack kCore, HolderLookup.Provider registries) {
        List<ArcaneCraftingPattern> patterns = getPatterns(kCore, registries);
        return patterns.stream().map(p -> (IPatternDetails) p).toList();
    }
}
