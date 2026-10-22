package com.heroicpaths.guild;

import com.heroicpaths.dungeon.DungeonType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Заказ гильдии. Цель — id вида существа, предмета, блока или данжа; «#» в начале означает тег
 * (например «#minecraft:zombies» — все зомби). Прогресс копится у взявшего заказ игрока.
 *
 * @param targetKey ключ перевода названия цели
 * @param icon      id предмета-значка
 * @param tier      0 — новичок, 1 — опытный, 2 — ветеран
 */
public record Order(String id, OrderType type, String target, String targetKey, String icon, int amount, int tier,
                    int rewardXp, int rewardEmeralds, ItemStack bonus, int progress) {
    /** С какого уровня можно взять заказ каждой ступени. */
    public static final int[] TIER_MIN_LEVEL = {1, 15, 30};
    private static final ChatFormatting[] TIER_COLORS = {ChatFormatting.GREEN, ChatFormatting.AQUA, ChatFormatting.LIGHT_PURPLE};

    /** Наказы на боссов хранят в {@code tier} номер данжа, и уровень берётся у него. */
    public int minLevel() {
        if (type == OrderType.BOSS) return bossDungeon().minLevel();
        return TIER_MIN_LEVEL[Math.max(0, Math.min(2, tier))];
    }

    public DungeonType bossDungeon() {
        DungeonType[] types = DungeonType.values();
        return types[Math.max(0, Math.min(types.length - 1, tier))];
    }

    public Order withProgress(int value) {
        return new Order(id, type, target, targetKey, icon, amount, tier, rewardXp, rewardEmeralds, bonus, Math.max(0, Math.min(amount, value)));
    }

    /** Охота, добыча и зачистка выполнены, когда прогресс дошёл до цели; доставка проверяется при сдаче. */
    public boolean isDone() {
        return type != OrderType.DELIVERY && progress >= amount;
    }

    public MutableComponent title() {
        return Component.translatable("order.heroicpaths.title." + type.id(), Component.translatable(targetKey), amount);
    }

    public MutableComponent tierName() {
        if (type == OrderType.BOSS) {
            return Component.translatable("dungeon.heroicpaths." + bossDungeon().id()).withStyle(ChatFormatting.RED);
        }
        return Component.translatable("order.heroicpaths.tier." + tier).withStyle(TIER_COLORS[Math.max(0, Math.min(2, tier))]);
    }

    public ItemStack iconStack() {
        ResourceLocation id = ResourceLocation.tryParse(icon);
        return id == null ? ItemStack.EMPTY : new ItemStack(BuiltInRegistries.ITEM.get(id));
    }

    private boolean isTag() {
        return target.startsWith("#");
    }

    @Nullable
    private ResourceLocation targetId() {
        return ResourceLocation.tryParse(isTag() ? target.substring(1) : target);
    }

    public boolean matchesEntity(EntityType<?> entityType) {
        ResourceLocation id = targetId();
        if (id == null) return false;
        return isTag() ? entityType.is(TagKey.create(Registries.ENTITY_TYPE, id)) : id.equals(BuiltInRegistries.ENTITY_TYPE.getKey(entityType));
    }

    public boolean matchesBlock(BlockState state) {
        ResourceLocation id = targetId();
        if (id == null) return false;
        return isTag() ? state.is(TagKey.create(Registries.BLOCK, id)) : id.equals(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    public boolean matchesItem(ItemStack stack) {
        ResourceLocation id = targetId();
        if (id == null || stack.isEmpty()) return false;
        return isTag() ? stack.is(TagKey.create(Registries.ITEM, id)) : id.equals(BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    public CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        tag.putString("type", type.id());
        tag.putString("target", target);
        tag.putString("targetKey", targetKey);
        tag.putString("icon", icon);
        tag.putInt("amount", amount);
        tag.putInt("tier", tier);
        tag.putInt("xp", rewardXp);
        tag.putInt("emeralds", rewardEmeralds);
        if (!bonus.isEmpty()) tag.put("bonus", bonus.save(registries));
        tag.putInt("progress", progress);
        return tag;
    }

    @Nullable
    public static Order load(HolderLookup.Provider registries, CompoundTag tag) {
        OrderType type = OrderType.byId(tag.getString("type"));
        if (type == null) return null;
        ItemStack bonus = tag.contains("bonus") ? ItemStack.parseOptional(registries, tag.getCompound("bonus")) : ItemStack.EMPTY;
        return new Order(tag.getString("id"), type, tag.getString("target"), tag.getString("targetKey"), tag.getString("icon"),
                Math.max(1, tag.getInt("amount")), tag.getInt("tier"), tag.getInt("xp"), tag.getInt("emeralds"), bonus, tag.getInt("progress"));
    }
}
