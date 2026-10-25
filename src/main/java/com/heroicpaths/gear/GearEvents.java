package com.heroicpaths.gear;

import com.heroicpaths.craft.GrandmasterForging;
import com.heroicpaths.registry.ModAttachments;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.event.GrindstoneEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Снаряжение — расходник. В бою оно не изнашивается, а при смерти надетое оружие и броня не выпадают, а теряют 5% прочности
 * (сломанное исчезает навсегда). Чинить оружие и броню нельзя: ни на наковальне, ни на точиле,
 * ни крафтом, ни починкой опытом. Инструменты и прочие предметы чинятся как обычно.
 */
public final class GearEvents {
    /** Слоты экипировки в инвентаре: броня 36–39 и вторая рука 40. Слот основной руки — выбранный на панели. */
    private static final int[] EQUIPMENT_SLOTS = {36, 37, 38, 39, Inventory.SLOT_OFFHAND};

    private GearEvents() {}

    // ================================================================ износ

    /**
     * Снаряжение мода не изнашивается само ({@code damageItem} возвращает 0), а ванильный лук после выстрела
     * получает прочность обратно на следующем тике: выстрел тратит её уже после этого события.
     */
    @SubscribeEvent
    public static void onUseStop(LivingEntityUseItemEvent.Stop event) {
        ItemStack bow = event.getItem();
        if (!bow.is(Items.BOW) || !(event.getEntity() instanceof ServerPlayer player)) return;
        int before = bow.getDamageValue();
        MinecraftServer server = player.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> {
            if (bow.is(Items.BOW) && bow.getDamageValue() > before) bow.setDamageValue(before);
        }));
    }

    // ================================================================ смерть

    /** Последним из обработчиков смерти, когда уже ясно, что игрок умирает, но вещи ещё не выпали. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && !player.isSpectator()) {
            boolean keepInventory = player.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
            applyDeathPenalty(player, !keepInventory);
        }
    }

    /**
     * Снимает прочность с надетого оружия и брони. Если {@code stash}, снаряжение убирается из инвентаря
     * (чтобы не выпало) и ждёт возрождения. Возвращает названия разрушенных предметов.
     */
    public static List<Component> applyDeathPenalty(ServerPlayer player, boolean stash) {
        Inventory inventory = player.getInventory();
        KeptGear kept = player.getData(ModAttachments.KEPT_GEAR);
        List<Component> destroyed = new ArrayList<>();
        int damaged = 0;
        int[] slots = new int[EQUIPMENT_SLOTS.length + 1];
        slots[0] = inventory.selected;
        System.arraycopy(EQUIPMENT_SLOTS, 0, slots, 1, EQUIPMENT_SLOTS.length);
        for (int slot : slots) {
            ItemStack stack = inventory.getItem(slot);
            if (!GearRules.isGear(stack)) continue;
            // Проклятие утраты работает как обычно: такой предмет исчезает.
            if (EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP)) continue;
            if (stack.isDamageableItem()) {
                int damage = stack.getDamageValue() + GearRules.deathLoss(stack);
                if (damage >= stack.getMaxDamage()) {
                    destroyed.add(stack.getHoverName());
                    inventory.setItem(slot, ItemStack.EMPTY);
                    continue;
                }
                stack.setDamageValue(damage);
                damaged++;
            }
            if (stash) {
                kept.add(slot, stack.copy());
                inventory.setItem(slot, ItemStack.EMPTY);
            }
        }
        if (damaged > 0) {
            player.sendSystemMessage(Component.translatable("message.heroicpaths.gear.kept",
                    Math.round(GearRules.DEATH_DURABILITY_LOSS * 100)).withStyle(ChatFormatting.GOLD));
        }
        for (Component name : destroyed) {
            player.sendSystemMessage(Component.translatable("message.heroicpaths.gear.destroyed", name).withStyle(ChatFormatting.RED));
        }
        return destroyed;
    }

    /** Возвращает снятое снаряжение в те же слоты; если слот занят — в любой свободный или под ноги. */
    public static void restore(ServerPlayer player) {
        if (!player.hasData(ModAttachments.KEPT_GEAR)) return;
        KeptGear kept = player.getData(ModAttachments.KEPT_GEAR);
        if (kept.isEmpty()) return;
        Inventory inventory = player.getInventory();
        for (KeptGear.Entry entry : kept.entries()) {
            ItemStack stack = entry.stack().copy();
            if (entry.slot() >= 0 && entry.slot() < inventory.getContainerSize() && inventory.getItem(entry.slot()).isEmpty()) {
                inventory.setItem(entry.slot(), stack);
            } else if (!inventory.add(stack)) {
                player.drop(stack, false);
            }
        }
        kept.clear();
        player.removeData(ModAttachments.KEPT_GEAR);
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) restore(player);
    }

    /** На случай, если сервер остановился, пока игрок лежал мёртвым. */
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.isAlive()) restore(player);
    }

    // ================================================================ запрет ремонта

    /**
     * Наковальня: оружие и броню нельзя чинить материалом или вторым таким же предметом и нельзя зачаровывать книгами —
     * их зачаровывает только рунный мастер. Переименовать можно.
     */
    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        ItemStack upgraded = GrandmasterForging.result(event.getLeft(), event.getRight());
        if (!upgraded.isEmpty()) {
            // Заготовка поднимает грейд вещи — это не ремонт, но только для гроссмейстера её ремесла.
            if (event.getPlayer() == null || !GrandmasterForging.canForge(event.getPlayer(), upgraded)) {
                event.setCanceled(true);
                return;
            }
            if (!event.getName().isBlank() && !event.getName().equals(upgraded.getHoverName().getString())) {
                upgraded.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(event.getName()));
            }
            event.setOutput(upgraded);
            event.setCost(GrandmasterForging.levelCost(upgraded));
            event.setMaterialCost(1);
            return;
        }
        if (isBlockedAnvilRepair(event.getLeft(), event.getRight())) event.setCanceled(true);
    }

    /** Опыт ремесла за переплавку на наковальне. */
    @SubscribeEvent
    public static void onAnvilTake(AnvilRepairEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getRight().getItem() instanceof com.heroicpaths.item.GearBlankItem) {
            ItemStack output = event.getOutput();
            com.heroicpaths.craft.ProfessionProgress.award(player, com.heroicpaths.craft.Profession.forItem(output),
                    10 * com.heroicpaths.item.Grades.of(output));
        }
    }

    public static boolean isBlockedAnvilRepair(ItemStack left, ItemStack right) {
        return GearRules.isGear(left) && !right.isEmpty();
    }

    /** Точило: объединять два предмета снаряжения нельзя (это чинит их). Снять чары с одного предмета — можно. */
    @SubscribeEvent
    public static void onGrindstone(GrindstoneEvent.OnPlaceItem event) {
        ItemStack top = event.getTopItem();
        ItemStack bottom = event.getBottomItem();
        if (!top.isEmpty() && !bottom.isEmpty() && (GearRules.isGear(top) || GearRules.isGear(bottom))) event.setCanceled(true);
    }

    /**
     * Починка опытом не трогает оружие и броню: опыт идёт на другие предметы с починкой или игроку.
     * Повторяет ванильную логику сферы опыта, но без снаряжения.
     */
    @SubscribeEvent
    public static void onPickupXp(PlayerXpEvent.PickupXp event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (EnchantmentHelper.getRandomItemWith(EnchantmentEffectComponents.REPAIR_WITH_XP, player,
                stack -> stack.isDamaged() && GearRules.isGear(stack)).isEmpty()) {
            return;
        }
        ExperienceOrb orb = event.getOrb();
        event.setCanceled(true);
        player.takeXpDelay = 2;
        player.take(orb, 1);
        int left = repairOtherItems(player, orb.getValue());
        if (left > 0) player.giveExperiencePoints(left);
        orb.count--;
        if (orb.count <= 0) orb.discard();
    }

    private static int repairOtherItems(ServerPlayer player, int value) {
        Optional<EnchantedItemInUse> item = EnchantmentHelper.getRandomItemWith(EnchantmentEffectComponents.REPAIR_WITH_XP, player,
                stack -> stack.isDamaged() && !GearRules.isGear(stack));
        if (item.isEmpty()) return value;
        ItemStack stack = item.get().itemStack();
        int repairable = EnchantmentHelper.modifyDurabilityToRepairFromXp(player.serverLevel(), stack, (int) (value * stack.getXpRepairRatio()));
        int repaired = Math.min(repairable, stack.getDamageValue());
        stack.setDamageValue(stack.getDamageValue() - repaired);
        if (repaired > 0) {
            int remaining = value - repaired * value / repairable;
            return remaining > 0 ? repairOtherItems(player, remaining) : 0;
        }
        return 0;
    }
}
