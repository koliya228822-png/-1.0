package com.heroicpaths.item;

import com.heroicpaths.craft.Profession;
import com.heroicpaths.craft.ProfessionProgress;
import com.heroicpaths.craft.Professions;
import com.heroicpaths.registry.ModComponents;
import com.heroicpaths.rpg.RpgManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Трактат ремесла: поднимает ремесло до своей ступени (подмастерье … мастер), если опыта ремесла уже хватает.
 * Продают жители-мастера: кузнецы — трактаты кузнеца, лучники, кожевники и пастухи — плотника,
 * библиотекари и священники — рунного мастера; книжник в лавке деревни продаёт все трактаты.
 */
public class ProfessionManualItem extends Item {
    private final Profession profession;

    public ProfessionManualItem(Profession profession, Properties properties) {
        super(properties);
        this.profession = profession;
    }

    public Profession profession() {
        return profession;
    }

    public static ItemStack create(Item item, int rank) {
        ItemStack stack = new ItemStack(item);
        stack.set(ModComponents.MANUAL_RANK, Math.max(Professions.MIN_RANK + 1, Math.min(Professions.MAX_RANK, rank)));
        return stack;
    }

    public static int rank(ItemStack stack) {
        return stack.getOrDefault(ModComponents.MANUAL_RANK, Professions.MIN_RANK + 1);
    }

    /** «Трактат кузнеца: подмастерье». */
    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(getDescriptionId() + ".ranked", Professions.rankName(rank(stack)));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer && ProfessionProgress.readManual(serverPlayer, profession, rank(stack))) {
            stack.consume(1, player);
            return InteractionResultHolder.consume(stack);
        }
        return level.isClientSide ? InteractionResultHolder.success(stack) : InteractionResultHolder.fail(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        int rank = rank(stack);
        tooltip.add(Component.translatable("item.heroicpaths.profession_manual.desc", profession.displayName(), Professions.rankName(rank))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.heroicpaths.profession_manual.xp", Professions.xpForRank(rank)).withStyle(ChatFormatting.GRAY));
        Player player = net.neoforged.fml.loading.FMLEnvironment.dist.isClient() ? ClientAccess.player() : null;
        if (player != null) {
            Professions professions = RpgManager.get(player).professions();
            tooltip.add(Component.translatable("item.heroicpaths.profession_manual.yours", professions.describe(profession),
                    professions.xp(profession)).withStyle(ChatFormatting.DARK_GRAY));
        }
    }

    /** Отдельный класс, чтобы сервер не загружал клиентский Minecraft. */
    private static final class ClientAccess {
        static Player player() {
            return net.minecraft.client.Minecraft.getInstance().player;
        }
    }
}
