package com.heroicpaths.dungeon;

import com.heroicpaths.party.Party;
import com.heroicpaths.party.PartyManager;
import com.heroicpaths.registry.ModBlockEntities;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * Состояние алтаря: какой проход данжа открыт, для кого и когда портал закроется.
 * Камень врат и свиток алтарь держит у себя: они сгорают при первом входе группы,
 * а если никто не вошёл, возвращаются тому, кто открыл врата.
 */
public class GateAltarBlockEntity extends BlockEntity {
    public static final int OPEN_TICKS = 6000;

    private int instanceIndex = -1;
    private long closesAt;
    @Nullable
    private UUID activator;
    private ItemStack escrowStone = ItemStack.EMPTY;
    private ItemStack escrowScroll = ItemStack.EMPTY;

    public GateAltarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.GATE_ALTAR.get(), pos, state);
    }

    public boolean isOpen() {
        return instanceIndex >= 0 && level != null && level.getGameTime() < closesAt;
    }

    public int instanceIndex() {
        return isOpen() ? instanceIndex : -1;
    }

    public long closesAt() {
        return closesAt;
    }

    public boolean hasEscrowedScroll(DungeonType type) {
        return type.scroll() != null && escrowScroll.is(type.scroll());
    }

    public boolean hasEscrow() {
        return !escrowStone.isEmpty() || !escrowScroll.isEmpty();
    }

    /**
     * Камень врат приложен к алтарю. Сначала проверки (средний уровень группы, свиток для данжей с 20 уровня),
     * потом постройка данжа. Камень и свиток забираются из инвентаря, только если всё прошло успешно.
     */
    public void activate(ServerPlayer player, ItemStack stone) {
        GateKey key = stone.get(ModComponents.GATE_KEY.get());
        DungeonType type = key == null ? null : DungeonType.byId(key.dungeon());
        if (type != null) open(player, type, stone);
    }

    /**
     * Дикие врата подземелья открываются без камня врат — в данж, к которому они ведут. После прохода
     * они запечатываются на 20 минут.
     */
    public void activateWild(ServerPlayer player, DungeonType type) {
        if (level == null) return;
        if (!isOpen() && level.getGameTime() < sealedUntil) {
            long minutes = (sealedUntil - level.getGameTime() + 1199) / 1200;
            player.displayClientMessage(Component.translatable("message.heroicpaths.gate_sealed", minutes).withStyle(ChatFormatting.GRAY), true);
            return;
        }
        open(player, type, ItemStack.EMPTY);
    }

    /** Сколько тиков дикие врата отдыхают после прохода. */
    public static final int WILD_REST_TICKS = 24000;
    private long sealedUntil;
    private boolean entered;

    private void open(ServerPlayer player, DungeonType type, ItemStack stone) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (isOpen()) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.gate_already_open"), true);
            return;
        }
        DungeonManager.Access access = DungeonManager.checkLevel(player, type);
        if (!access.allowed()) {
            player.sendSystemMessage(access.reason().copy().withStyle(ChatFormatting.RED));
            return;
        }
        int scrollSlot = -1;
        if (type.requiresScroll()) {
            scrollSlot = DungeonManager.findScroll(player, type);
            if (scrollSlot < 0) {
                player.sendSystemMessage(Component.translatable("message.heroicpaths.access.scroll",
                        new ItemStack(type.scroll()).getHoverName()).withStyle(ChatFormatting.RED));
                return;
            }
        }
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        BlockPos returnPos = worldPosition.relative(facing.getOpposite());
        Party party = PartyManager.partyOf(player);
        List<UUID> roster = PartyManager.roster(player);
        DungeonInstance instance = DungeonManager.create(serverLevel.getServer(), type, roster, party == null ? null : party.id(),
                serverLevel.dimension(), returnPos);
        if (instance == null) {
            player.displayClientMessage(Component.translatable("message.heroicpaths.gate_failed").withStyle(ChatFormatting.RED), true);
            return;
        }
        escrowStone = stone.isEmpty() ? ItemStack.EMPTY : stone.split(1);
        if (scrollSlot >= 0) escrowScroll = player.getInventory().getItem(scrollSlot).split(1);
        player.getInventory().setChanged();
        activator = player.getUUID();
        instanceIndex = instance.index;
        closesAt = serverLevel.getGameTime() + OPEN_TICKS;
        setChanged();
        setPortal(true);

        Component message = Component.translatable("message.heroicpaths.gate_opened", type.displayName(), instance.partySize,
                Math.round((type.healthMultiplier(instance.partySize) - 1) * 100)).withStyle(ChatFormatting.LIGHT_PURPLE);
        for (Player nearby : serverLevel.getEntitiesOfClass(Player.class, new AABB(worldPosition).inflate(32))) {
            nearby.sendSystemMessage(message);
        }
        serverLevel.playSound(null, worldPosition, SoundEvents.END_PORTAL_SPAWN, SoundSource.BLOCKS, 0.8f, 1.2f);
    }

    /** Группа вошла: камень и свиток сгорают. */
    public void consumeEscrow() {
        escrowStone = ItemStack.EMPTY;
        escrowScroll = ItemStack.EMPTY;
        entered = true;
        setChanged();
    }

    public void serverTick() {
        if (instanceIndex >= 0 && level != null && level.getGameTime() >= closesAt) closeGate();
    }

    /** Закрывает врата. Если никто так и не вошёл, камень и свиток возвращаются тому, кто их принёс. */
    public void closeGate() {
        setPortal(false);
        returnEscrow();
        // Дикие врата после прохода запечатываются, чтобы подземелье не проходили бесконечно подряд.
        if (entered && level != null && getBlockState().getBlock() instanceof DungeonGateBlock) {
            sealedUntil = level.getGameTime() + WILD_REST_TICKS;
        }
        entered = false;
        instanceIndex = -1;
        activator = null;
        setChanged();
    }

    public long sealedUntil() {
        return sealedUntil;
    }

    private void returnEscrow() {
        if (!hasEscrow() || !(level instanceof ServerLevel serverLevel)) return;
        ServerPlayer owner = activator == null ? null : serverLevel.getServer().getPlayerList().getPlayer(activator);
        for (ItemStack stack : List.of(escrowStone, escrowScroll)) {
            if (stack.isEmpty()) continue;
            if (owner != null) {
                DungeonManager.give(owner, stack);
            } else if (activator != null) {
                DungeonData.get(serverLevel.getServer()).addPendingReturn(activator, stack);
            } else {
                Containers.dropItemStack(serverLevel, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, stack);
            }
        }
        if (owner != null) {
            owner.sendSystemMessage(Component.translatable("message.heroicpaths.gate_items_returned").withStyle(ChatFormatting.GRAY));
        }
        escrowStone = ItemStack.EMPTY;
        escrowScroll = ItemStack.EMPTY;
    }

    /** Заполняет (или очищает) проём рамки 3×4 в 2 блоках перед алтарём блоками портала. */
    public void setPortal(boolean open) {
        if (level == null || !getBlockState().hasProperty(HorizontalDirectionalBlock.FACING)) return;
        Direction facing = getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        Direction lateral = facing.getClockWise();
        BlockPos base = worldPosition.relative(facing, 2);
        java.util.List<BlockPos> cells = new java.util.ArrayList<>();
        for (int side = -1; side <= 1; side++) {
            for (int y = 0; y < 4; y++) {
                BlockPos pos = base.relative(lateral, side).above(y);
                BlockState current = level.getBlockState(pos);
                if (open && (current.isAir() || current.is(ModBlocks.DUNGEON_PORTAL.get()))) cells.add(pos);
                else if (!open && current.is(ModBlocks.DUNGEON_PORTAL.get())) level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
        if (open && !cells.isEmpty()) DungeonPortalBlock.fill(level, cells, lateral.getAxis());
    }

    /** Ищет открытый алтарь рядом с порталом. */
    @Nullable
    public static GateAltarBlockEntity findNear(ServerLevel level, BlockPos portal) {
        for (BlockPos pos : BlockPos.betweenClosed(portal.offset(-4, -4, -4), portal.offset(4, 4, 4))) {
            if (level.getBlockEntity(pos) instanceof GateAltarBlockEntity altar && altar.isOpen()) return altar;
        }
        return null;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("instance", instanceIndex);
        tag.putLong("closesAt", closesAt);
        if (activator != null) tag.putUUID("activator", activator);
        if (!escrowStone.isEmpty()) tag.put("stone", escrowStone.save(registries));
        if (!escrowScroll.isEmpty()) tag.put("scroll", escrowScroll.save(registries));
        tag.putLong("sealedUntil", sealedUntil);
        tag.putBoolean("entered", entered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        instanceIndex = tag.contains("instance") ? tag.getInt("instance") : -1;
        closesAt = tag.getLong("closesAt");
        activator = tag.hasUUID("activator") ? tag.getUUID("activator") : null;
        escrowStone = ItemStack.parseOptional(registries, tag.getCompound("stone"));
        escrowScroll = ItemStack.parseOptional(registries, tag.getCompound("scroll"));
        sealedUntil = tag.getLong("sealedUntil");
        entered = tag.getBoolean("entered");
    }
}
