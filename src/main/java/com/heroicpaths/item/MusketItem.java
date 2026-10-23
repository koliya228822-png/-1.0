package com.heroicpaths.item;

import com.heroicpaths.entity.MusketBall;
import com.heroicpaths.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Мушкет: бьёт сильнее лука, но стреляет недалеко и медленно перезаряжается. Стреляет пулями из инвентаря.
 * Умение «Твёрдая рука» удваивает урон следующего выстрела.
 */
public class MusketItem extends Item {
    public static final float BASE_DAMAGE = 14f;
    public static final int RELOAD_TICKS = 40;
    public static final float SPEED = 5f;
    public static final String STEADY_AIM = "heroicpaths.steady_aim_until";

    public MusketItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack musket = player.getItemInHand(hand);
        ItemStack ammo = findAmmo(player);
        if (ammo.isEmpty() && !player.getAbilities().instabuild) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.heroicpaths.musket_no_ammo").withStyle(ChatFormatting.RED), true);
            }
            return InteractionResultHolder.fail(musket);
        }
        if (level instanceof ServerLevel serverLevel) {
            float damage = BASE_DAMAGE;
            boolean steady = player.getPersistentData().getLong(STEADY_AIM) > level.getGameTime();
            if (steady) {
                damage *= 2f;
                player.getPersistentData().remove(STEADY_AIM);
            }
            MusketBall ball = new MusketBall(level, player, musket);
            ball.setBaseDamage(damage / SPEED);
            ball.setCritArrow(steady);
            ball.shootFromRotation(player, player.getXRot(), player.getYRot(), 0f, SPEED, 0.5f);
            serverLevel.addFreshEntity(ball);
            Vec3 muzzle = player.getEyePosition().add(player.getLookAngle().scale(1.2));
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE, muzzle.x, muzzle.y, muzzle.z, 6, 0.1, 0.1, 0.1, 0.02);
            serverLevel.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 3, 0.05, 0.05, 0.05, 0.02);
            serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.GENERIC_EXPLODE.value(),
                    SoundSource.PLAYERS, 0.6f, 1.8f);
            if (!player.getAbilities().instabuild) ammo.shrink(1);
            musket.hurtAndBreak(1, player, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
        player.getCooldowns().addCooldown(this, RELOAD_TICKS);
        return InteractionResultHolder.consume(musket);
    }

    /** Есть ли чем зарядить мушкет. */
    public static boolean hasAmmo(Player player) {
        return !findAmmo(player).isEmpty();
    }

    private static ItemStack findAmmo(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.MUSKET_BALL.get())) return stack;
        }
        return ItemStack.EMPTY;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.heroicpaths.musket.hint", Math.round(BASE_DAMAGE), RELOAD_TICKS / 20f)
                .withStyle(ChatFormatting.GRAY));
    }
}
