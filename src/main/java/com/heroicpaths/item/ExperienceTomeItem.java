package com.heroicpaths.item;

import com.heroicpaths.rpg.RpgData;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Даёт опыт класса. Только для тестов: из лута, наград и торговли книги убраны, остались в творческой вкладке. */
public class ExperienceTomeItem extends Item {
    private static final int XP_AMOUNT = 150;
    private final int amount;
    private final String descriptionKey;

    public ExperienceTomeItem(Properties properties) {
        this(properties, XP_AMOUNT, "item.heroicpaths.experience_tome.desc");
    }

    /** Тот же предмет опыта с другим количеством (камень предков). */
    public ExperienceTomeItem(Properties properties, int amount, String descriptionKey) {
        super(properties);
        this.amount = amount;
        this.descriptionKey = descriptionKey;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (RpgManager.get(player).getLevel() >= RpgData.MAX_LEVEL) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("message.heroicpaths.max_level"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            RpgManager.addXp(serverPlayer, amount);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 1f);
        }
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(descriptionKey, amount).withStyle(ChatFormatting.GRAY));
    }
}
