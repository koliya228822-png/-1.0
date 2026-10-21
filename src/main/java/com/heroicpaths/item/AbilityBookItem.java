package com.heroicpaths.item;

import com.heroicpaths.registry.ModComponents;
import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import com.heroicpaths.rpg.Subclass;
import com.heroicpaths.rpg.ability.AbilityDef;
import com.heroicpaths.rpg.ability.AbilityRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.util.RandomSource;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Книга умения: находят в данжах и покупают у наставников. Чтение тратит книгу и одно очко умений.
 * Учиться может только подкласс, которому это умение доступно.
 */
public class AbilityBookItem extends Item {
    public AbilityBookItem(Properties properties) {
        super(properties);
    }

    public static ItemStack create(Item item, AbilityDef def) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModComponents.ABILITY.get(), def.id());
        return stack;
    }

    /** Случайная книга среди умений, которые можно выучить до указанного уровня. */
    public static ItemStack random(Item item, RandomSource random, int maxLevel) {
        List<AbilityDef> pool = AbilityRegistry.bookAbilities(maxLevel);
        if (pool.isEmpty()) return new ItemStack(item);
        return create(item, pool.get(random.nextInt(pool.size())));
    }

    @Nullable
    public static AbilityDef ability(ItemStack stack) {
        return AbilityRegistry.get(stack.get(ModComponents.ABILITY.get()));
    }

    @Override
    public Component getName(ItemStack stack) {
        AbilityDef def = ability(stack);
        return def == null ? super.getName(stack) : Component.translatable("item.heroicpaths.ability_book.named", def.name());
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return ability(stack) != null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        AbilityDef def = ability(stack);
        if (def == null) return InteractionResultHolder.pass(stack);
        if (player instanceof ServerPlayer serverPlayer && RpgManager.learnAbility(serverPlayer, def)) {
            stack.consume(1, player);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        AbilityDef def = ability(stack);
        if (def == null) {
            tooltip.add(Component.translatable("item.heroicpaths.ability_book.empty").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(def.description().withStyle(ChatFormatting.GRAY));
        MutableComponent learners = Component.empty();
        List<Subclass> subclasses = AbilityRegistry.learners(def);
        for (int i = 0; i < subclasses.size(); i++) {
            if (i > 0) learners.append(", ");
            learners.append(subclasses.get(i).displayName());
        }
        tooltip.add(Component.translatable("item.heroicpaths.ability_book.learners", learners).withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("item.heroicpaths.ability_book.level", def.unlockLevel()).withStyle(ChatFormatting.DARK_AQUA));
        if (def.weapon() != com.heroicpaths.rpg.ability.WeaponReq.NONE) {
            tooltip.add(Component.translatable("item.heroicpaths.ability_book.weapon", def.weapon().displayName()).withStyle(ChatFormatting.DARK_AQUA));
        }
        tooltip.add(Component.translatable("item.heroicpaths.ability_book.hint").withStyle(ChatFormatting.DARK_GRAY));
    }

    /** Подсказка на клиенте: можно ли выучить книгу прямо сейчас. */
    @Nullable
    public static String problemFor(RpgData data, ItemStack stack) {
        AbilityDef def = ability(stack);
        return def == null ? null : RpgManager.learnProblem(data, def);
    }
}
