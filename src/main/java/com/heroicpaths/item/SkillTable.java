package com.heroicpaths.item;

import com.heroicpaths.network.SkillTablePayload;
import com.heroicpaths.registry.ModBlocks;
import com.heroicpaths.registry.ModItems;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.ability.AbilityDef;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EnchantingTableBlock;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

/**
 * Стол зачарований больше не зачаровывает: он делает книги умений.
 * <ul>
 *     <li>Книга умения: книга + 8 лазурита + 5 уровней опыта — случайное умение, которое может выучить ваш подкласс.</li>
 *     <li>2-й уровень стола — вокруг него 6 и больше полок умений (там же, где ставят обычные книжные полки):
 *     книга + 32 лазурита + алмаз + 30 уровней — книга выбранного уникального умения.</li>
 * </ul>
 * Полки умений крафтят из трёх книг умений и шести досок.
 */
public final class SkillTable {
    public static final int SHELVES_FOR_TIER_2 = 6;
    public static final int BOOK_LAPIS = 8, BOOK_LEVELS = 5;
    public static final int UNIQUE_LAPIS = 32, UNIQUE_DIAMONDS = 1, UNIQUE_LEVELS = 30;
    private static final double REACH = 8;

    private SkillTable() {}

    /** Сколько полок умений стоит вокруг стола (между полкой и столом должно быть пусто). */
    public static int shelves(Level level, BlockPos table) {
        int count = 0;
        for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (level.getBlockState(table.offset(offset)).is(ModBlocks.SKILL_BOOKSHELF.get())
                    && level.isEmptyBlock(table.offset(offset.getX() / 2, offset.getY(), offset.getZ() / 2))) count++;
        }
        return count;
    }

    public static int tier(Level level, BlockPos table) {
        return shelves(level, table) >= SHELVES_FOR_TIER_2 ? 2 : 1;
    }

    @SubscribeEvent
    public static void onUseTable(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (!level.getBlockState(event.getPos()).is(Blocks.ENCHANTING_TABLE)) return;
        // Shift с предметом в руке ставит блок рядом со столом, как обычно.
        if (event.getEntity().isSecondaryUseActive() && !event.getItemStack().isEmpty()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
        if (event.getEntity() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, new SkillTablePayload(event.getPos(), "", shelves(level, event.getPos())));
        }
    }

    /** Умения, которые выучит подкласс игрока (из книг, по его уровню). */
    public static List<AbilityDef> bookPool(Player player) {
        RpgData data = RpgManager.get(player);
        List<AbilityDef> pool = new ArrayList<>();
        for (AbilityDef def : AbilityRegistry.bookAbilities(Math.max(data.getLevel(), 1))) {
            if (data.getSubclass() == null || AbilityRegistry.isLearnable(data.getSubclass(), def)) pool.add(def);
        }
        return pool;
    }

    /** Уникальные умения, книги которых можно сделать: для подкласса игрока, а без подкласса — все. */
    public static List<AbilityDef> uniquePool(Player player) {
        RpgData data = RpgManager.get(player);
        return data.getSubclass() == null ? AbilityRegistry.uniques() : AbilityRegistry.uniqueFor(data.getSubclass());
    }

    public static boolean craft(ServerPlayer player, BlockPos table, String uniqueId) {
        Level level = player.level();
        if (!level.getBlockState(table).is(Blocks.ENCHANTING_TABLE) || player.distanceToSqr(table.getCenter()) > REACH * REACH) return false;
        boolean unique = !uniqueId.isEmpty();
        AbilityDef def;
        if (unique) {
            if (tier(level, table) < 2) return fail(player, "message.heroicpaths.skill_table_tier");
            def = AbilityRegistry.get(uniqueId);
            if (def == null || !def.unique() || !uniquePool(player).contains(def)) return false;
        } else {
            List<AbilityDef> pool = bookPool(player);
            if (pool.isEmpty()) return fail(player, "message.heroicpaths.skill_table_nothing");
            def = pool.get(player.getRandom().nextInt(pool.size()));
        }
        int lapis = unique ? UNIQUE_LAPIS : BOOK_LAPIS, diamonds = unique ? UNIQUE_DIAMONDS : 0, levels = unique ? UNIQUE_LEVELS : BOOK_LEVELS;
        boolean creative = player.getAbilities().instabuild;
        if (!creative && (count(player, Items.BOOK) < 1 || count(player, Items.LAPIS_LAZULI) < lapis || count(player, Items.DIAMOND) < diamonds
                || player.experienceLevel < levels)) {
            return fail(player, unique ? "message.heroicpaths.skill_table_cost_unique" : "message.heroicpaths.skill_table_cost");
        }
        if (!creative) {
            take(player, Items.BOOK, 1);
            take(player, Items.LAPIS_LAZULI, lapis);
            take(player, Items.DIAMOND, diamonds);
            player.giveExperienceLevels(-levels);
        }
        ItemStack book = AbilityBookItem.create(unique ? ModItems.UNIQUE_ABILITY_BOOK.get() : ModItems.ABILITY_BOOK.get(), def);
        if (!player.getInventory().add(book)) player.drop(book, false);
        level.playSound(null, table, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 1f, unique ? 0.7f : 1f);
        player.displayClientMessage(Component.translatable("message.heroicpaths.skill_table_done", def.name()).withStyle(ChatFormatting.AQUA), true);
        return true;
    }

    private static boolean fail(ServerPlayer player, String key) {
        player.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        return false;
    }

    public static int count(Player player, Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) if (stack.is(item)) total += stack.getCount();
        return total;
    }

    private static void take(Player player, Item item, int amount) {
        for (ItemStack stack : player.getInventory().items) {
            if (amount <= 0) return;
            if (!stack.is(item)) continue;
            int taken = Math.min(amount, stack.getCount());
            stack.shrink(taken);
            amount -= taken;
        }
    }
}
