package thaumicenergistics.assembler;

import appeng.api.ids.AEComponents;
import appeng.api.implementations.items.IMemoryCard;
import appeng.api.implementations.items.MemoryCardMessages;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.upgrades.IUpgradeInventory;
import appeng.api.upgrades.UpgradeInventories;
import appeng.core.definitions.AEItems;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.client.ParticleApi;
import thaumcraft.api.wands.WandApi;
import thaumcraft.api.wands.WandParts;
import thaumicenergistics.blocks.ProviderBlockEntity;
import thaumicenergistics.knowledge.ArcaneCraftingPattern;
import thaumicenergistics.knowledge.HandlerKnowledgeCore;
import thaumicenergistics.knowledge.ItemKnowledgeCore;
import thaumicenergistics.vis.DigiVisSourceData;

/** Live server host. Displays never own ingredients; the accepted queue owns all in-flight output. */
public final class ArcaneAssemblerBlockEntity extends ProviderBlockEntity implements AssemblerHost {
    private static final Component SOURCE_NAME = Component.translatable("thaumicenergistics.gui.digivis.source.name");
    private final AssemblerInventory inventory = new AssemblerInventory(stack -> stack.getItem() instanceof ItemKnowledgeCore,
            slot -> inventoryChanged());
    private final IUpgradeInventory upgrades = UpgradeInventories.forMachine(AssemblerRegistration.ITEM.get(), 4,
            this::inventoryChanged);
    private final HandlerKnowledgeCore core = new HandlerKnowledgeCore();
    private final AssemblerTaskQueue tasks = new AssemblerTaskQueue();
    private final AssemblerVisBuffer vis = new AssemblerVisBuffer();
    private final AssemblerCraftingProvider crafting;
    private DigiVisSourceData source;
    private ItemStack sceptre = ItemStack.EMPTY;
    private boolean loading, broken;
    private int refillTicks;
    private int visualTicks;
    private boolean publishedBusy;
    private VisualState publishedState;
    private VisualState clientState = new VisualState(false, 0, 0, List.of(0, 0, 0, 0, 0, 0));

    private record VisualState(boolean crafting, int ticks, int speed, List<Integer> vis) { }

    public ArcaneAssemblerBlockEntity(BlockPos pos, BlockState state) {
        super(AssemblerRegistration.BLOCK_ENTITY.get(), pos, state, AssemblerRules.IDLE_AE);
        crafting = new AssemblerCraftingProvider(getMainNode(),
                () -> level instanceof ServerLevel server ? server : null,
                this::current, this::patterns, tasks, getMachineSource(), this::queueChanged, this::craftStarted);
        getMainNode().addService(ICraftingProvider.class, crafting);
    }

    private boolean current() {
        return !broken && !isRemoved() && level instanceof ServerLevel server && server.getServer().isSameThread()
                && server.hasChunkAt(worldPosition) && server.getBlockEntity(worldPosition) == this;
    }

    private List<ArcaneCraftingPattern> patterns() {
        refreshCore();
        return core.getPatterns();
    }

    private void refreshCore() {
        if (!(level instanceof ServerLevel) || loading) return;
        ItemStack stack = inventory.getItem(AssemblerRules.CORE);
        if (!stack.isEmpty() && stack.getItem() instanceof ItemKnowledgeCore) {
            if (core.isHandlingCore(stack)) return;
            core.open(stack, level.registryAccess());
        } else {
            if (!core.hasCore()) return;
            core.close();
        }
        var outputs = core.getStoredOutputs();
        for (int i = 0; i < AssemblerRules.PATTERN_COUNT; i++) {
            inventory.display(AssemblerRules.FIRST_PATTERN + i, i < outputs.size() ? outputs.get(i) : ItemStack.EMPTY);
        }
        // Request after the snapshot is installed, avoiding recursive rereads of a stale core.
        if (crafting != null && getMainNode().isReady()) crafting.patternsChanged();
    }

    private void inventoryChanged() {
        if (loading || level == null || level.isClientSide) return;
        saveChanges();
        refreshCore();
    }

    private void craftStarted(ItemStack product) {
        if (current() && !product.isEmpty()) AssemblerAnimationPayload.send((ServerLevel) level, worldPosition, speed(), product);
    }

    private void queueChanged() {
        saveChanges();
        // Do not lose the start of a four-tick craft between the five-tick snapshots.
        if (current() && publishedBusy != tasks.isBusy()) {
            publishedBusy = tasks.isBusy();
            markForUpdate();
        }
    }

    private VisualState worldState() {
        var amounts = new ArrayList<Integer>(6);
        for (int i = 0; i < 6; i++) amounts.add(vis.get(i));
        return new VisualState(tasks.isBusy(), tasks.paidTicks(), speed(), List.copyOf(amounts));
    }

    private AssemblerArmor armor() {
        if (sceptre.isEmpty()) {
            sceptre = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("thaumcraft:casting_sceptre")));
            WandApi.parts(sceptre, new WandParts("silverwood", "thaumium"));
        }
        return AssemblerArmor.read(inventory, aspect -> WandApi.craftingModifier(sceptre, null, aspect));
    }

    public void serverTick() {
        if (!current()) return;
        refreshCore();
        if (++refillTicks >= AssemblerRules.REFILL_INTERVAL) {
            refillTicks = 0;
            if (isActive() && source != null) {
                var endpoint = source.resolve(level.getServer(), getMainNode().getGrid());
                if (endpoint != null && endpoint.isActive()) {
                    for (int i = 0; i < 6; i++) {
                        if (!current() || !isActive() || source == null
                                || source.resolve(level.getServer(), getMainNode().getGrid()) != endpoint) break;
                        int room = vis.room(i);
                        if (room <= 0) continue;
                        Aspect primal = Aspect.getAspect(AssemblerVisBuffer.PRIMALS.get(i));
                        int received = endpoint.consumeVis(primal, room);
                        if (received < 0 || received > room) throw new IllegalStateException("Vis source exceeded request");
                        if (received > 0) { vis.add(i, received); saveChanges(); }
                    }
                }
            }
        }
        if (tasks.isBusy()) crafting.tick(speed(), armor(), vis);
        ItemStack target = ItemStack.EMPTY;
        if (!tasks.definition().isEmpty()) {
            var decoded = appeng.api.crafting.PatternDetailsHelper.decodePattern(
                    appeng.api.stacks.AEItemKey.of(tasks.definition()), level);
            if (decoded instanceof ArcaneCraftingPattern pattern) target = pattern.getResult();
        }
        if (!ItemStack.matches(inventory.getItem(AssemblerRules.TARGET), target))
            inventory.display(AssemblerRules.TARGET, target);
        if (++visualTicks >= AssemblerRules.REFILL_INTERVAL) {
            visualTicks = 0;
            VisualState next = worldState();
            if (!next.equals(publishedState)) {
                publishedState = next;
                markForUpdate();
            }
        }
    }

    private int speed() { return upgrades.getInstalledUpgrades(AEItems.SPEED_CARD); }
    @Override public AssemblerInventory inventory() { return inventory; }
    @Override public IUpgradeInventory upgrades() { return upgrades; }
    @Override public boolean valid(Player player) { return player.isAlive() && !player.isSpectator() && super.valid(player); }
    @Override public AssemblerStatePayload snapshot(int menuId) {
        var values = new ArrayList<Integer>(6);
        var modifiers = new ArrayList<Float>(6);
        var armor = armor();
        for (int i = 0; i < 6; i++) {
            values.add(vis.get(i));
            modifiers.add(armor.modifiers().get(Aspect.getAspect(AssemblerVisBuffer.PRIMALS.get(i))));
        }
        return new AssemblerStatePayload(menuId, isActive(), tasks.isBusy(), tasks.paidTicks(), speed(), values, modifiers);
    }

    public boolean memoryCard(Player player, ItemStack stack) {
        if (!(stack.getItem() instanceof IMemoryCard card)) return false;
        if (level.isClientSide) return true;
        if (!valid(player)) return true;
        Component name = stack.get(AEComponents.EXPORTED_SETTINGS_SOURCE);
        if (SOURCE_NAME.equals(name)) {
            source = DigiVisSourceData.read(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag());
            if (source != null) card.notifyUser(player, MemoryCardMessages.SETTINGS_LOADED);
            saveChanges();
        } else if (name == null) {
            source = null;
            card.notifyUser(player, MemoryCardMessages.SETTINGS_CLEARED);
            saveChanges();
        }
        return true;
    }

    public void restoreItem(ItemStack stack) {
        var data = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (data.contains("stored_vis", 10)) vis.load(data.getCompound("stored_vis"));
        saveChanges();
    }

    public ItemStack dismantleItem() {
        ItemStack result = new ItemStack(AssemblerRegistration.ITEM.get());
        if (vis.snapshot().values().stream().anyMatch(amount -> amount > 0))
            CustomData.update(DataComponents.CUSTOM_DATA, result, data -> data.put("stored_vis", vis.save()));
        return result;
    }

    public void breakAndDrop() {
        if (broken || level == null || level.isClientSide) return;
        broken = true;
        for (int i = 0; i < AssemblerRules.SLOTS; i++) {
            if (i != AssemblerRules.CORE && i < AssemblerRules.FIRST_ARMOR) continue;
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + .5,
                    worldPosition.getY() + .5, worldPosition.getZ() + .2, stack.copy());
        }
        for (int i = 0; i < upgrades.size(); i++) {
            ItemStack stack = upgrades.getStackInSlot(i);
            if (!stack.isEmpty()) Containers.dropItemStack(level, worldPosition.getX() + .5,
                    worldPosition.getY() + .5, worldPosition.getZ() + .2, stack.copy());
        }
        // Source explicitly discards in-flight work on break; unload does not call this path.
        tasks.discardOnBreak();
        core.close();
    }

    @Override public void onReady() { super.onReady(); refreshCore(); crafting.patternsChanged(); }
    @Override protected void onProviderGridChanged() {
        super.onProviderGridChanged();
        if (crafting != null && getMainNode().isReady()) crafting.patternsChanged();
    }

    @Override protected void writeToStream(RegistryFriendlyByteBuf buffer) {
        super.writeToStream(buffer);
        VisualState state = worldState();
        buffer.writeBoolean(state.crafting());
        if (state.crafting()) buffer.writeInt(state.ticks());
        for (int amount : state.vis()) buffer.writeInt(amount);
        buffer.writeInt(state.speed());
    }

    @Override protected boolean readFromStream(RegistryFriendlyByteBuf buffer) {
        super.readFromStream(buffer);
        boolean busy = buffer.readBoolean();
        int ticks = busy ? buffer.readInt() : 0;
        var amounts = new ArrayList<Integer>(6);
        for (int i = 0; i < 6; i++) amounts.add(Math.max(0, buffer.readInt()));
        clientState = new VisualState(busy, ticks, buffer.readInt(), List.copyOf(amounts));
        if (busy && level != null && level.isClientSide)
            for (int i = 0; i < 2; i++) ParticleApi.blockRunes(level, worldPosition.getX(),
                    worldPosition.getY(), worldPosition.getZ(), .5F, 0, .5F, 10, -.1F);
        return true;
    }

    /** Observer data is independent of the server-owned task and menu inventory. */
    public boolean visuallyCrafting() { return level != null && level.isClientSide ? clientState.crafting() : tasks.isBusy(); }
    public int visualCraftTicks() { return level != null && level.isClientSide ? clientState.ticks() : tasks.paidTicks(); }
    public int visualSpeed() { return level != null && level.isClientSide ? clientState.speed() : speed(); }
    public int visualVis(int primal) { return level != null && level.isClientSide ? clientState.vis().get(primal) : vis.get(primal); }

    @Override public void saveAdditional(CompoundTag data, HolderLookup.Provider registries) {
        super.saveAdditional(data, registries);
        data.put("AssemblerInventory", inventory.save(registries));
        upgrades.writeToNBT(data, "AssemblerUpgrades", registries);
        data.put("stored_vis", vis.save());
        data.put("AssemblerTask", tasks.save(registries));
        if (source != null) data.put("vis_interface", source.write());
        else data.remove("vis_interface");
    }

    @Override public void loadTag(CompoundTag data, HolderLookup.Provider registries) {
        loading = true;
        try {
            super.loadTag(data, registries);
            core.close();
            inventory.load(data.getCompound("AssemblerInventory"), registries);
            upgrades.readFromNBT(data, "AssemblerUpgrades", registries);
            vis.load(data.getCompound("stored_vis"));
            tasks.load(data.getCompound("AssemblerTask"), registries);
            source = data.contains("vis_interface", 10) ? DigiVisSourceData.read(data.getCompound("vis_interface")) : null;
        } finally { loading = false; }
    }
}
