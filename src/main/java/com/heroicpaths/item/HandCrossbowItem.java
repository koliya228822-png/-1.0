package com.heroicpaths.item;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Locale;
import net.minecraft.world.item.Item;
import java.util.function.Consumer;
import org.jetbrains.annotations.Nullable;

/**
 * Ручной арбалет, как в BG3: одноручный, заряжается быстрее обычного арбалета, но бьёт слабее.
 * Два ручных арбалета в обеих руках заряжаются вместе и стреляют залпом. Делает плотник у дубильного чана.
 */
public class HandCrossbowItem extends CrossbowItem {
    private final int grade;
    private final int chargeTicks;
    private final float power;

    public HandCrossbowItem(int grade, int chargeTicks, float power, Properties properties) {
        super(properties);
        this.grade = grade;
        this.chargeTicks = chargeTicks;
        this.power = power;
    }

    public int grade() {
        return grade;
    }

    /** Время зарядки в тиках с учётом «Быстрой перезарядки». */
    public int chargeDuration(ItemStack stack, LivingEntity shooter) {
        float seconds = EnchantmentHelper.modifyCrossbowChargingTime(stack, shooter, chargeTicks / 20f);
        return Math.max(1, Mth.floor(seconds * 20f));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return chargeDuration(stack, entity) + 3;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (isCharged(stack)) {
            fire(level, player, hand, stack);
            // Залп: заряженный ручной арбалет во второй руке стреляет вместе с этим.
            InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            ItemStack otherStack = player.getItemInHand(other);
            if (otherStack.getItem() instanceof HandCrossbowItem otherCrossbow && isCharged(otherStack)) {
                otherCrossbow.fire(level, player, other, otherStack);
            }
            return InteractionResultHolder.consume(stack);
        }
        if (!player.getProjectile(stack).isEmpty()) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.fail(stack);
    }

    private void fire(Level level, Player player, InteractionHand hand, ItemStack stack) {
        ChargedProjectiles projectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
        float velocity = projectiles != null && projectiles.contains(Items.FIREWORK_ROCKET) ? 1.6f : power;
        performShooting(level, player, hand, stack, velocity, 1.0f, null);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int used = getUseDuration(stack, entity) - timeLeft;
        if (used < chargeDuration(stack, entity) || isCharged(stack) || !load(entity, stack)) return;
        // Вторая рука: ручной арбалет там заряжается заодно.
        ItemStack other = entity.getMainHandItem() == stack ? entity.getOffhandItem() : entity.getMainHandItem();
        if (other.getItem() instanceof HandCrossbowItem && !isCharged(other)) load(entity, other);
        level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_END.value(),
                entity.getSoundSource(), 1.0f, 1.0f / (level.getRandom().nextFloat() * 0.5f + 1.0f) + 0.3f);
    }

    /** Заряжает арбалет стрелой из инвентаря (или ракетой из второй руки). */
    public static boolean load(LivingEntity shooter, ItemStack crossbow) {
        List<ItemStack> projectiles = draw(crossbow, shooter.getProjectile(crossbow), shooter);
        if (projectiles.isEmpty()) return false;
        crossbow.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(projectiles));
        return true;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (level.isClientSide) return;
        int used = getUseDuration(stack, entity) - remaining;
        int charge = chargeDuration(stack, entity);
        if (used == Math.max(1, charge / 5)) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_START.value(),
                    SoundSource.PLAYERS, 0.4f, 1.3f);
        } else if (used == charge / 2) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE.value(),
                    SoundSource.PLAYERS, 0.4f, 1.3f);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.translatable("item.heroicpaths.hand_crossbow.charge",
                String.format(Locale.ROOT, "%.2f", chargeTicks / 20f)).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.heroicpaths.hand_crossbow.pair").withStyle(ChatFormatting.DARK_AQUA));
    }

    /** Снаряжение не изнашивается в бою: прочность уходит только при смерти. */
    @Override
    public <T extends LivingEntity> int damageItem(ItemStack stack, int amount, @Nullable T entity, Consumer<Item> onBroken) {
        return 0;
    }
}
