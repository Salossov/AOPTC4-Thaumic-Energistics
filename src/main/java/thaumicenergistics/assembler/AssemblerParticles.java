package thaumicenergistics.assembler;

import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumicenergistics.ThaumicEnergistics;

/** Sprite registration for the original AE2 crafting sparkle; never a gameplay resource. */
public final class AssemblerParticles {
    private static final DeferredRegister<ParticleType<?>> TYPES = DeferredRegister.create(Registries.PARTICLE_TYPE, ThaumicEnergistics.ID);
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> ENERGY =
            TYPES.register("assembler_energy", () -> new SimpleParticleType(true) {});
    public static void initialize(IEventBus bus) { TYPES.register(bus); }
    private AssemblerParticles() {}
}
