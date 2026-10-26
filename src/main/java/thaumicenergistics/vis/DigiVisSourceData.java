package thaumicenergistics.vis;

import appeng.api.networking.IGrid;
import appeng.api.parts.IPartHost;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import thaumicenergistics.api.DigiVisSource;

/** Persistent identity, resolved against loaded owners on every use: never force-load a link. */
public record DigiVisSourceData(GlobalPos location, Direction side, long uid) {
    public static DigiVisSourceData of(DigiVisSource source) {
        return new DigiVisSourceData(source.getLocation(),source.getSide(),source.getUID());
    }
    public DigiVisSource resolve(MinecraftServer server, IGrid destination) {
        if (server == null || destination == null) return null;
        var level = server.getLevel(location.dimension());
        if (level == null) return null;
        var chunk = level.getChunkSource().getChunkNow(location.pos().getX() >> 4, location.pos().getZ() >> 4);
        if (chunk == null) return null;
        var owner = chunk.getBlockEntity(location.pos(), net.minecraft.world.level.chunk.LevelChunk.EntityCreationType.CHECK);
        Object candidate;
        if (side == null) candidate = owner;
        else {
            var host = owner instanceof IPartHost partHost ? partHost : null;
            candidate = host == null ? null : host.getPart(side);
        }
        return candidate instanceof DigiVisSource source && source.getUID() == uid
                && source.getGrid() == destination ? source : null;
    }
    public CompoundTag write() {
        var tag = new CompoundTag();
        tag.putString("dimension",location.dimension().location().toString());
        tag.putLong("position",location.pos().asLong()); tag.putByte("side",(byte)(side == null ? -1 : side.ordinal()));
        tag.putLong("uid",uid); return tag;
    }
    public static DigiVisSourceData read(CompoundTag tag) {
        var dimension = ResourceLocation.tryParse(tag.getString("dimension"));
        int side = tag.getByte("side");
        if (dimension == null || !tag.contains("position",4) || !tag.contains("uid",4)
                || !tag.contains("side",1) || side < -1 || side > 5) return null;
        return new DigiVisSourceData(GlobalPos.of(ResourceKey.create(Registries.DIMENSION,dimension),BlockPos.of(tag.getLong("position"))),
                side == -1 ? null : Direction.values()[side],tag.getLong("uid"));
    }
}
