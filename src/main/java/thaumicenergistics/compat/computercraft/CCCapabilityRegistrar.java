package thaumicenergistics.compat.computercraft;

import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.api.EssentiaProviderAccess;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Isolated registrar for CC:Tweaked peripheral capabilities.
 * Loaded ONLY when computercraft is detected by {@link CCIntegrationRegistration}.
 */
public final class CCCapabilityRegistrar {
    private static final Map<BlockEntity, EssentiaProviderPeripheral> PERIPHERAL_CACHE = new WeakHashMap<>();

    private CCCapabilityRegistrar() {}

    public static void register(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
            PeripheralCapability.get(),
            ThaumicEnergistics.ESSENTIA_PROVIDER_ENTITY.get(),
            (blockEntity, side) -> getPeripheral(blockEntity)
        );
    }

    public static synchronized IPeripheral getPeripheral(BlockEntity blockEntity) {
        if (blockEntity == null || blockEntity.isRemoved() || !(blockEntity instanceof EssentiaProviderAccess access)) {
            return null;
        }
        return PERIPHERAL_CACHE.computeIfAbsent(blockEntity, be -> new EssentiaProviderPeripheral(access));
    }
}
