package thaumicenergistics.knowledge;

import io.netty.handler.codec.DecoderException;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import thaumicenergistics.ThaumicEnergistics;

/**
 * Server-to-client save state synchronization for Knowledge Inscriber.
 */
public record KnowledgeInscriberStatePayload(int containerId, int saveState, boolean justSaved) implements CustomPacketPayload {
    public static final Type<KnowledgeInscriberStatePayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "knowledge_inscriber_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, KnowledgeInscriberStatePayload> CODEC = StreamCodec.of(
            (buffer, value) -> {
                buffer.writeVarInt(value.containerId);
                buffer.writeByte(value.saveState);
                buffer.writeBoolean(value.justSaved);
            },
            buffer -> {
                int id = buffer.readVarInt();
                int state = buffer.readUnsignedByte();
                boolean justSaved = buffer.readBoolean();
                if (state < 0 || state > 4) {
                    throw new DecoderException("Invalid CoreSaveState ordinal: " + state);
                }
                return new KnowledgeInscriberStatePayload(id, state, justSaved);
            }
    );

    @Override
    public Type<KnowledgeInscriberStatePayload> type() {
        return TYPE;
    }

    public static void handle(KnowledgeInscriberStatePayload payload, Player player) {
        if (player.containerMenu instanceof KnowledgeInscriberMenu menu && menu.containerId == payload.containerId) {
            menu.receiveState(KnowledgeInscriberMenu.CoreSaveState.fromOrdinal(payload.saveState), payload.justSaved);
        }
    }

}
