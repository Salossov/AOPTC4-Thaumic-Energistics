package thaumicenergistics.extras;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.neoforge.common.conditions.ICondition;

/**
 * Datapack condition gating the extras recipes behind the same feature seam as
 * the research registration: {"type":"thaumicenergistics:extras_feature","feature":"wrench_focus"}.
 * Mirrors the original registerCrafting-only-when-available behavior without a
 * second config system.
 */
public record ExtrasFeatureCondition(String feature) implements ICondition {
    public static final MapCodec<ExtrasFeatureCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance
            .group(Codec.STRING.fieldOf("feature").forGetter(ExtrasFeatureCondition::feature))
            .apply(instance, ExtrasFeatureCondition::new));

    @Override
    public boolean test(IContext context) {
        return ExtrasConfig.enabled(feature);
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
