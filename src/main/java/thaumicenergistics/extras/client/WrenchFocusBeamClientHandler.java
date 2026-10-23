package thaumicenergistics.extras.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aspects.Aspects;
import thaumcraft.api.client.WandBeamApi;
import thaumicenergistics.extras.WrenchFocusBeamPayload;

/**
 * Isolated client-only handler for the AE wrench focus beam.
 * Kept strictly in this client package so dedicated servers never load
 * {@link Minecraft} or client particle settings when registering payloads.
 */
public final class WrenchFocusBeamClientHandler {
    public static void render(WrenchFocusBeamPayload payload) {
        var minecraft = Minecraft.getInstance();
        var level = minecraft.level;
        if (level == null || !(level.getEntity(payload.sourceId()) instanceof Player source)) {
            return;
        }
        // Original client gate: gameSettings.particleSetting != 2 (minimal).
        if (minecraft.options.particles().get() == ParticleStatus.MINIMAL) {
            return;
        }
        WandBeamApi.emit(source, new WandBeamApi.Beam(
                new Vec3(payload.targetX(), payload.targetY(), payload.targetZ()),
                0, Aspects.ENERGY.getColor(), false, 0.2f));
    }

    private WrenchFocusBeamClientHandler() {}
}
