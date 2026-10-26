package thaumicenergistics.assembler;

import java.util.Objects;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import thaumicenergistics.ThaumicEnergistics;

/** One cosmetic event per accepted task, independent of menu and later BE snapshots. */
public record AssemblerAnimationPayload(ResourceKey<Level> dimension, BlockPos position, int upgrades,
                                        ItemStack product) implements CustomPacketPayload {
    public AssemblerAnimationPayload {
        Objects.requireNonNull(dimension); position = Objects.requireNonNull(position).immutable();
        product = Objects.requireNonNull(product).copy();
        if (product.isEmpty() || upgrades < 0 || upgrades > AssemblerRules.MAX_SPEED_UPGRADES)
            throw new IllegalArgumentException("Invalid assembler animation");
    }
    @Override public ItemStack product() { return product.copy(); }
    public int rate() { return 10 + 9 * upgrades; }
    public int lifetime() { return (int) Math.ceil(Math.max(1, 100F / rate())) + 2; }
    public static final Type<AssemblerAnimationPayload> TYPE = new Type<>(ThaumicEnergistics.id("arcane_assembler_animation"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AssemblerAnimationPayload> CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeResourceKey(value.dimension); buffer.writeBlockPos(value.position); buffer.writeVarInt(value.upgrades);
                ItemStack.STREAM_CODEC.encode(buffer, value.product);
            }, buffer -> new AssemblerAnimationPayload(buffer.readResourceKey(net.minecraft.core.registries.Registries.DIMENSION),
                    buffer.readBlockPos(), buffer.readVarInt(), ItemStack.STREAM_CODEC.decode(buffer)));
    private static Consumer<AssemblerAnimationPayload> client = ignored -> {};
    public static void installClient(Consumer<AssemblerAnimationPayload> receiver) { client = Objects.requireNonNull(receiver); }
    public static void send(ServerLevel level, BlockPos position, int upgrades, ItemStack product) {
        if (!level.getServer().isSameThread()) throw new IllegalStateException("Server thread required");
        PacketDistributor.sendToPlayersNear(level, null, position.getX(), position.getY(), position.getZ(), 32,
                new AssemblerAnimationPayload(level.dimension(), position, upgrades, product));
    }
    @Override public Type<AssemblerAnimationPayload> type() { return TYPE; }
    @EventBusSubscriber(modid=ThaumicEnergistics.ID, bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event) {
            event.registrar("1").playToClient(TYPE, CODEC, (payload, context) -> context.enqueueWork(() -> client.accept(payload)));
        }
    }
}
