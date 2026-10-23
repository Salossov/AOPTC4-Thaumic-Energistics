package thaumicenergistics.golems;

import io.netty.buffer.ByteBuf;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics.ThaumicEnergistics;

/** Client display state only; network transactions always use the server connection. */
public record GolemWirelessStatusPayload(int entityId, UUID uuid, boolean inRange) implements CustomPacketPayload {
    public static final Type<GolemWirelessStatusPayload> TYPE = new Type<>(ThaumicEnergistics.id("golem_wireless_status"));
    public static final StreamCodec<ByteBuf, GolemWirelessStatusPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GolemWirelessStatusPayload::entityId,
            UUIDUtil.STREAM_CODEC, GolemWirelessStatusPayload::uuid,
            ByteBufCodecs.BOOL, GolemWirelessStatusPayload::inRange,
            GolemWirelessStatusPayload::new);
    // Identity keys prevent stale entity ids, reconnects or dimension changes reusing old display state.
    private static final Map<LivingEntity, Boolean> CLIENT_STATUS = new WeakHashMap<>();

    public static GolemWirelessStatusPayload of(Entity entity, boolean inRange) {
        return new GolemWirelessStatusPayload(entity.getId(), entity.getUUID(), inRange);
    }

    @Override
    public Type<GolemWirelessStatusPayload> type() {
        return TYPE;
    }

    public static void handle(GolemWirelessStatusPayload payload, Player player) {
        if (!player.level().isClientSide) return;
        Entity entity = player.level().getEntity(payload.entityId());
        if (entity instanceof LivingEntity living && living.getUUID().equals(payload.uuid())) {
            CLIENT_STATUS.put(living, payload.inRange());
        }
    }

    public static boolean inRange(LivingEntity entity) {
        return entity.level().isClientSide && Boolean.TRUE.equals(CLIENT_STATUS.get(entity));
    }

    public static void forget(Entity entity) {
        if (entity instanceof LivingEntity living) CLIENT_STATUS.remove(living);
    }
}
