package thaumicenergistics.storage;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import com.mojang.serialization.MapCodec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import thaumicenergistics.ThaumicEnergistics;

public final class EssentiaKeyType extends AEKeyType {
    public static final EssentiaKeyType INSTANCE = new EssentiaKeyType();
    private EssentiaKeyType() {
        super(ThaumicEnergistics.id("essentia"), EssentiaKey.class, Component.translatable("keytype.thaumicenergistics.essentia"));
    }
    @Override public MapCodec<EssentiaKey> codec() { return EssentiaKey.CODEC; }
    @Override public AEKey readFromPacket(RegistryFriendlyByteBuf input) { return new EssentiaKey(input.readResourceLocation()); }
    @Override public int getAmountPerByte() { return 2; }
    @Override public int getAmountPerOperation() { return 1; }
}
