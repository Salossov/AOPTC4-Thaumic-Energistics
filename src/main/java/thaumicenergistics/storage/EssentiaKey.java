package thaumicenergistics.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.MapCodec;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;

/** Canonical aspect identity only; quantities are separate longs in AE2. */
public final class EssentiaKey extends AEKey {
    public static final MapCodec<EssentiaKey> CODEC = ResourceLocation.CODEC.fieldOf("aspect")
            .xmap(EssentiaKey::new, EssentiaKey::getId);
    private final ResourceLocation id;

    public EssentiaKey(ResourceLocation id) { this.id = Objects.requireNonNull(id); }
    public static EssentiaKey of(Aspect aspect) { return ofTag(Objects.requireNonNull(aspect).getTag()); }
    public static EssentiaKey ofTag(String tag) {
        CellContents.requireTag(tag);
        return new EssentiaKey(tag.contains(":") ? ResourceLocation.parse(tag) : ResourceLocation.fromNamespaceAndPath("thaumcraft", tag));
    }
    public String tag() { return id.getNamespace().equals("thaumcraft") ? id.getPath() : id.toString(); }
    public Aspect aspect() { return Aspect.getAspect(tag()); }
    @Override public AEKeyType getType() { return EssentiaKeyType.INSTANCE; }
    @Override public AEKey dropSecondary() { return this; }
    @Override public Object getPrimaryKey() { return id; }
    @Override public ResourceLocation getId() { return id; }
    @Override public boolean hasComponents() { return false; }
    @Override public void writeToPacket(RegistryFriendlyByteBuf output) { output.writeResourceLocation(id); }
    @Override public CompoundTag toTag(HolderLookup.Provider registries) {
        var result = new CompoundTag(); result.putString("aspect", id.toString()); return result;
    }
    @Override protected Component computeDisplayName() {
        var aspect = aspect();
        return aspect == null ? Component.literal(id.toString()) : aspect.translationKey() == null
                ? Component.literal(aspect.getName()) : Component.translatable(aspect.translationKey());
    }
    @Override public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
        // Same uncontained-resource treatment as AE2 AEFluidKey. Cells themselves retain their item data.
        // Audit the original gas-backed AE2 interface destruction path before marking fidelity verified.
    }
    @Override public boolean equals(Object other) { return other instanceof EssentiaKey key && id.equals(key.id); }
    @Override public int hashCode() { return id.hashCode(); }
}
