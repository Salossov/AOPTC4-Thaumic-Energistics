package thaumicenergistics.config;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.mojang.serialization.Codec;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Native recipe loading condition, sharing the startup switches used for research. */
public record ContentFeatureCondition(String feature) implements ICondition {
    public static final MapCodec<ContentFeatureCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("feature").forGetter(ContentFeatureCondition::feature)).apply(instance, ContentFeatureCondition::new));
    @Override public boolean test(IContext context) { return ContentFeature.enabled(feature); }
    @Override public MapCodec<? extends ICondition> codec() { return CODEC; }
}
