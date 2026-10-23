package thaumicenergistics.golems;

import appeng.api.implementations.blockentities.ICrankable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * BlockEntity for the Iron and Thaumium Gearbox.
 * Direct port of 1.7.10 TileGearBox:
 * - 6 directional shaft connections
 * - Cranking distributor: BASE_POWER (6) / (number of ready crankables)
 * - Power accumulation per shaft up to REQUIRED_POWER (18) before turning
 * - Network synchronization throttled to MIN_TICKS_PER_SYNC (6 ticks)
 */
public class GearboxBlockEntity extends BlockEntity {
    public static final String NBT_KEY_CRANKABLES = "crankables";
    public static final String NBT_KEY_ROTATION = "rotation";
    public static final String NBT_KEY_ISTHAUMBOX = "isThaumbox";

    public static final int BASE_POWER = 6;
    public static final int REQUIRED_POWER = 18;
    public static final int MIN_TICKS_PER_SYNC = 6;
    public static final int SIDE_COUNT = 6;
    public static final float FULL_ROTATION = (float) Math.PI;

    private int crankableCount = 0;
    public final boolean[] sideIsFacingCrankable = new boolean[SIDE_COUNT];
    private final boolean[] canTurn = new boolean[SIDE_COUNT];
    private final int[] shafts = new int[SIDE_COUNT];

    private boolean hasNewCranks = false;
    private int syncTickCount = 0;
    private boolean isReady = false;
    private boolean isThaumiumGearbox = false;

    public float shaftRotation = 0.0F;

    public GearboxBlockEntity(BlockPos pos, BlockState state) {
        super(GolemsRegistrar.GEARBOX_BE.get(), pos, state);
        if (state.getBlock() instanceof GearboxBlock gb) {
            this.isThaumiumGearbox = gb.isThaumium();
        }
    }

    public GearboxBlockEntity(BlockPos pos, BlockState state, boolean isThaumium) {
        super(GolemsRegistrar.GEARBOX_BE.get(), pos, state);
        this.isThaumiumGearbox = isThaumium;
    }

    public boolean isThaumiumGearbox() {
        return isThaumiumGearbox;
    }

    public void setThaumiumGearbox(boolean isThaumium) {
        this.isThaumiumGearbox = isThaumium;
    }

    public int getCrankableCount() {
        return crankableCount;
    }

    public boolean canTurn(int side) {
        return side >= 0 && side < SIDE_COUNT && canTurn[side];
    }

    public int getShaftPower(int side) {
        return side >= 0 && side < SIDE_COUNT ? shafts[side] : 0;
    }

    public int[] getShafts() {
        return shafts;
    }

    private void onReady() {
        this.updateCrankables();
        if (this.getBlockState().getBlock() instanceof GearboxBlock gb) {
            this.isThaumiumGearbox = gb.isThaumium();
        }
        this.isReady = true;
    }

    /**
     * Checks all 6 adjacent sides for compatible ICrankable blocks without force-loading chunks.
     */
    public void updateCrankables() {
        this.crankableCount = 0;
        if (this.level == null) {
            return;
        }

        for (int i = 0; i < SIDE_COUNT; i++) {
            Direction dir = Direction.from3DDataValue(i);
            this.sideIsFacingCrankable[i] = false;

            BlockPos neighborPos = this.worldPosition.relative(dir);
            if (this.level.hasChunkAt(neighborPos)) {
                ICrankable crankable = ICrankable.get(this.level, neighborPos, dir.getOpposite());
                if (crankable != null) {
                    this.crankableCount++;
                    this.sideIsFacingCrankable[i] = true;
                }
            }
        }
    }

    /**
     * Calculates power transferred to each turning shaft (6 / count).
     * Note: Explicitly resets canTurn[i] to false when neighbor cannot turn,
     * adapting 1.7.10's omission to prevent stale power distribution.
     */
    public int calculateTransferPower() {
        int powerDivisor = 0;
        if (this.level == null) {
            return 0;
        }

        for (int i = 0; i < SIDE_COUNT; i++) {
            Direction dir = Direction.from3DDataValue(i);
            BlockPos neighborPos = this.worldPosition.relative(dir);

            if (this.level.hasChunkAt(neighborPos)) {
                ICrankable crankable = ICrankable.get(this.level, neighborPos, dir.getOpposite());
                if (crankable != null && crankable.canTurn()) {
                    this.canTurn[i] = true;
                    powerDivisor++;
                    continue;
                }
            }
            this.canTurn[i] = false;
        }

        if (powerDivisor == 0) {
            return 0;
        }

        return BASE_POWER / powerDivisor;
    }

    /**
     * Updates power accumulation on each shaft and applies turns when threshold 18 is reached.
     */
    public void updateShafts(int powerTransferred) {
        if (this.level == null) {
            return;
        }

        for (int i = 0; i < SIDE_COUNT; i++) {
            if (this.canTurn[i]) {
                Direction dir = Direction.from3DDataValue(i);
                BlockPos neighborPos = this.worldPosition.relative(dir);

                if (this.level.hasChunkAt(neighborPos)) {
                    ICrankable crankable = ICrankable.get(this.level, neighborPos, dir.getOpposite());
                    if (crankable != null) {
                        this.shafts[i] += powerTransferred;
                        if (this.shafts[i] >= REQUIRED_POWER) {
                            this.shafts[i] = 0;
                            crankable.applyTurn();
                        }
                        continue;
                    }
                }
            }
            this.shafts[i] = 0;
        }
    }

    /**
     * Cranks the gearbox.
     * @return true if work was performed or client sided success.
     */
    public boolean crankGearbox() {
        if (this.level == null || this.level.isClientSide) {
            return true;
        }

        if (this.crankableCount <= 0) {
            return false;
        }

        int powerTransferred = this.calculateTransferPower();
        if (powerTransferred == 0) {
            return false;
        }

        this.updateShafts(powerTransferred);
        this.hasNewCranks = true;
        return true;
    }

    /**
     * Called every tick on both client and server.
     */
    public void tick(Level level, BlockPos pos, BlockState state) {
        if (!this.isReady) {
            this.onReady();
        }

        if (!level.isClientSide) {
            // Server side: sync throttled to MIN_TICKS_PER_SYNC (6 ticks)
            this.syncTickCount++;
            if (this.syncTickCount >= MIN_TICKS_PER_SYNC) {
                this.syncTickCount--;
                if (this.hasNewCranks) {
                    level.sendBlockUpdated(pos, state, state, 3);
                    // Do NOT clear hasNewCranks here! It is cleared when update tag/packet is created.
                    this.syncTickCount = 0;
                }
            }
        } else {
            // Client side: smooth rotation damping
            if (this.shaftRotation != 0) {
                this.shaftRotation -= 0.20F;
                if (this.shaftRotation < 0.001F) {
                    this.shaftRotation = 0;
                }
            }
        }
    }

    public byte getCrankableSideMask() {
        byte mask = 0;
        for (int i = 0; i < SIDE_COUNT; i++) {
            if (this.sideIsFacingCrankable[i]) {
                mask |= (byte) (1 << i);
            }
        }
        return mask;
    }

    public void setCrankableSideMask(byte mask) {
        for (int i = 0; i < SIDE_COUNT; i++) {
            this.sideIsFacingCrankable[i] = (mask & (1 << i)) != 0;
        }
    }

    private void writeSyncData(CompoundTag tag) {
        tag.putByte(NBT_KEY_CRANKABLES, getCrankableSideMask());
        tag.putBoolean(NBT_KEY_ISTHAUMBOX, this.isThaumiumGearbox);
    }

    private void readSyncData(CompoundTag tag) {
        if (tag.contains(NBT_KEY_CRANKABLES)) {
            setCrankableSideMask(tag.getByte(NBT_KEY_CRANKABLES));
        }
        if (tag.contains(NBT_KEY_ROTATION)) {
            if (this.shaftRotation < 1.0F) {
                this.shaftRotation += FULL_ROTATION * 2;
            }
        }
        if (tag.contains(NBT_KEY_ISTHAUMBOX)) {
            this.isThaumiumGearbox = tag.getBoolean(NBT_KEY_ISTHAUMBOX);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeSyncData(tag);
        // Do NOT clear hasNewCranks or serialize temporary rotation pulse to world save
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readSyncData(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        writeSyncData(tag);
        if (this.hasNewCranks) {
            tag.putBoolean(NBT_KEY_ROTATION, true);
            this.hasNewCranks = false;
        }
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookupProvider) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            readSyncData(tag);
        }
    }
}
