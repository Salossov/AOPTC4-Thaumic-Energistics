package thaumicenergistics.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable, copy-safe item data component representation.
 * Unknown addon aspects and custom tags are retained, never discarded.
 * Defensively copies collections so external code cannot mutate contents or bypass save callbacks.
 */
public record CellContents(Map<String, Long> amounts, List<String> partitions, List<String> slots) {
    public static final CellContents EMPTY = new CellContents(Map.of(), List.of(), List.of());
    public static final Codec<CellContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.LONG.validate(value -> value > 0
                    ? DataResult.success(value) : DataResult.error(() -> "Cell amounts must be positive")))
                    .optionalFieldOf("amounts", Map.of()).forGetter(CellContents::amounts),
            Codec.STRING.listOf().optionalFieldOf("partitions", List.of()).forGetter(CellContents::partitions),
            Codec.STRING.listOf().optionalFieldOf("slots", List.of()).forGetter(CellContents::slots)
    ).apply(instance, CellContents::new));

    public CellContents(Map<String, Long> amounts, List<String> partitions) {
        this(amounts, partitions, List.of());
    }

    public CellContents {
        Objects.requireNonNull(amounts, "amounts cannot be null");
        Objects.requireNonNull(partitions, "partitions cannot be null");
        Objects.requireNonNull(slots, "slots cannot be null");
        var copy = new LinkedHashMap<String, Long>();
        amounts.forEach((tag, amount) -> {
            requireTag(tag);
            if (amount == null || amount <= 0) {
                throw new IllegalArgumentException("Nonpositive cell amount for aspect: " + tag);
            }
            copy.put(tag, amount);
        });
        partitions.forEach(CellContents::requireTag);

        // Empty strings retain array holes from original HandlerItemEssentiaCell.
        // Missing/duplicate assignments cannot erase amounts; unassigned stored tags append.
        var layout = new ArrayList<String>();
        var assigned = new HashSet<String>();
        for (var tag : slots) {
            Objects.requireNonNull(tag, "slot tag cannot be null");
            if (!tag.isEmpty()) {
                requireTag(tag);
            }
            layout.add(!tag.isEmpty() && copy.containsKey(tag) && assigned.add(tag) ? tag : "");
        }
        for (var tag : copy.keySet()) {
            if (assigned.add(tag)) {
                layout.add(tag);
            }
        }
        // Clean up trailing empty holes so layout does not grow unbounded when slots are cleared at end
        while (!layout.isEmpty() && layout.get(layout.size() - 1).isEmpty()) {
            layout.remove(layout.size() - 1);
        }

        var ordered = new LinkedHashMap<String, Long>();
        for (var tag : layout) {
            if (!tag.isEmpty()) {
                ordered.put(tag, copy.get(tag));
            }
        }
        amounts = Collections.unmodifiableMap(ordered);
        // Original replaceAspectInPartitionList may create duplicates; retain order and entries.
        partitions = List.copyOf(partitions);
        slots = List.copyOf(layout);
    }

    public static void requireTag(String tag) {
        if (tag == null || tag.length() > 128 || !tag.matches("[a-z0-9_.-]+(?::[a-z0-9_./-]+)?")) {
            throw new IllegalArgumentException("Invalid aspect tag: " + tag);
        }
    }
}
