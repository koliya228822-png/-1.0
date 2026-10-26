package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Реролл товаров жителя. Клиент → сервер: обновить товары жителя, с которым идёт торговля (цена не важна).
 * Сервер → клиент: сколько изумрудов стоит следующий реролл; -1 — у этого жителя реролла нет.
 */
public record TradeRerollPayload(int price) implements CustomPacketPayload {
    public static final TradeRerollPayload REQUEST = new TradeRerollPayload(0);
    public static final CustomPacketPayload.Type<TradeRerollPayload> TYPE = new CustomPacketPayload.Type<>(HeroicPaths.id("trade_reroll"));
    public static final StreamCodec<ByteBuf, TradeRerollPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(TradeRerollPayload::new, TradeRerollPayload::price);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
