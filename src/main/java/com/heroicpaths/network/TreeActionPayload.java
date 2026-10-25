package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Клиент → сервер: действие в меню прокачки.
 * <ul>
 *     <li>ALLOCATE / REFUND — изучить или забыть узел дерева (value = номер узла);</li>
 *     <li>CHOOSE_SUBCLASS — выбрать подкласс персонажу без подкласса (text = id подкласса);</li>
 *     <li>ADD_STAT — вложить очко в характеристику (value = номер характеристики);</li>
 *     <li>RANK_UP — повысить ранг умения (text = id умения);</li>
 *     <li>BIND — поставить умение в слот (text = id умения, value = слот);</li>
 *     <li>CONVERT_XP — перевести ванильный опыт (text = id ремесла или пусто — в опыт персонажа).</li>
 * </ul>
 */
public record TreeActionPayload(Action action, int value, String text) implements CustomPacketPayload {
    public enum Action { ALLOCATE, REFUND, CHOOSE_SUBCLASS, ADD_STAT, RANK_UP, BIND, CONVERT_XP }

    public static final CustomPacketPayload.Type<TreeActionPayload> TYPE =
            new CustomPacketPayload.Type<>(HeroicPaths.id("tree_action"));
    public static final StreamCodec<ByteBuf, TreeActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT.map(i -> Action.values()[Math.floorMod(i, Action.values().length)], Action::ordinal),
            TreeActionPayload::action,
            ByteBufCodecs.VAR_INT, TreeActionPayload::value,
            ByteBufCodecs.STRING_UTF8, TreeActionPayload::text,
            TreeActionPayload::new);

    public static TreeActionPayload allocate(int node) {
        return new TreeActionPayload(Action.ALLOCATE, node, "");
    }

    public static TreeActionPayload refund(int node) {
        return new TreeActionPayload(Action.REFUND, node, "");
    }

    public static TreeActionPayload chooseSubclass(String subclass) {
        return new TreeActionPayload(Action.CHOOSE_SUBCLASS, 0, subclass);
    }

    public static TreeActionPayload addStat(int stat) {
        return new TreeActionPayload(Action.ADD_STAT, stat, "");
    }

    public static TreeActionPayload rankUp(String ability) {
        return new TreeActionPayload(Action.RANK_UP, 0, ability);
    }

    public static TreeActionPayload bind(String ability, int slot) {
        return new TreeActionPayload(Action.BIND, slot, ability);
    }

    public static TreeActionPayload convertXp(String profession) {
        return new TreeActionPayload(Action.CONVERT_XP, 0, profession);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
