package thaumicenergistics.knowledge;

import io.netty.handler.codec.DecoderException;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import thaumicenergistics.ThaumicEnergistics;

/**
 * Client-to-server action payload for Knowledge Inscriber.
 * Validates containerId, menu ownership, distance <= 64, live BE, and non-fake player.
 */
public record KnowledgeInscriberActionPayload(int containerId, int action, int patternIndex) implements CustomPacketPayload {
    public static final int ACTION_FULL_UPDATE = 0;
    public static final int ACTION_SAVE_DELETE = 1;
    public static final int ACTION_CLEAR = 2;
    public static final int ACTION_SELECT_PATTERN = 3;

    public static final Type<KnowledgeInscriberActionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "knowledge_inscriber_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KnowledgeInscriberActionPayload> CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.containerId);
                buffer.writeByte(value.action);
                buffer.writeVarInt(value.patternIndex);
            },
            buffer -> {
                int id = buffer.readVarInt();
                int act = buffer.readUnsignedByte();
                int idx = buffer.readVarInt();
                if (act < 0 || act > 3 || idx < -1 || idx > 21) {
                    throw new DecoderException("Invalid KnowledgeInscriberActionPayload parameters");
                }
                return new KnowledgeInscriberActionPayload(id, act, idx);
            }
    );

    @Override
    public Type<KnowledgeInscriberActionPayload> type() {
        return TYPE;
    }

    public static void handle(KnowledgeInscriberActionPayload payload, ServerPlayer player) {
        if (player == null || player instanceof FakePlayer) return;
        if (player.containerMenu instanceof KnowledgeInscriberMenu menu && menu.containerId == payload.containerId) {
            menu.handleAction(player, payload.action, payload.patternIndex);
        }
    }
}
