package thaumicenergistics.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable, copy-safe item data. Unknown addon aspects are retained, never discarded. */
public record CellContents(Map<String, Long> amounts, List<String> partitions) {
    public static final CellContents EMPTY = new CellContents(Map.of(), List.of());
    public static final Codec<CellContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.LONG.validate(value -> value > 0
                    ? DataResult.success(value) : DataResult.error(() -> "Cell amounts must be positive")))
                    .optionalFieldOf("amounts", Map.of()).forGetter(CellContents::amounts),
            Codec.STRING.listOf().optionalFieldOf("partitions", List.of()).forGetter(CellContents::partitions)
    ).apply(instance, CellContents::new));

    public CellContents {
        Objects.requireNonNull(amounts);
        Objects.requireNonNull(partitions);
        var copy = new LinkedHashMap<String, Long>();
        amounts.forEach((tag, amount) -> {
            requireTag(tag);
            if (amount == null || amount <= 0) throw new IllegalArgumentException("Nonpositive cell amount");
            copy.put(tag, amount);
        });
        partitions.forEach(CellContents::requireTag);
        amounts = Collections.unmodifiableMap(copy);
        // Original replaceAspectInPartitionList may create duplicates; retain order and entries.
        partitions = List.copyOf(partitions);
    }

    public static void requireTag(String tag) {
        if (tag == null || tag.length() > 128 || !tag.matches("[a-z0-9_.-]+(?::[a-z0-9_./-]+)?")) {
            throw new IllegalArgumentException("Invalid aspect tag: " + tag);
        }
    }
}
