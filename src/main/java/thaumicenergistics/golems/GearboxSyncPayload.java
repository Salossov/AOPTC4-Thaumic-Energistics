package thaumicenergistics.golems;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import thaumicenergistics.ThaumicEnergistics;

/**
 * Server -> Client sync payload for Gearbox shaft rotation and side connectivity.
 */
public record GearboxSyncPayload(BlockPos pos, byte sideMask, boolean rotationImpulse) implements CustomPacketPayload {
    public static final Type<GearboxSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.ID, "gearbox_sync"));

    public static final StreamCodec<ByteBuf, GearboxSyncPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            GearboxSyncPayload::pos,
            ByteBufCodecs.BYTE,
            GearboxSyncPayload::sideMask,
            ByteBufCodecs.BOOL,
            GearboxSyncPayload::rotationImpulse,
            GearboxSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GearboxSyncPayload payload, Player player) {
        if (player.level().getBlockEntity(payload.pos()) instanceof GearboxBlockEntity gearbox) {
            gearbox.setCrankableSideMask(payload.sideMask());
            if (payload.rotationImpulse() && gearbox.shaftRotation < 1.0F) {
                gearbox.shaftRotation += GearboxBlockEntity.FULL_ROTATION * 2;
            }
        }
    }
}
