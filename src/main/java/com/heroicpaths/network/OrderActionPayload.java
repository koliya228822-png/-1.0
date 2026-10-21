package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Клиент → сервер: действие с заказом на доске (взять предложение, сдать или бросить свой заказ). */
public record OrderActionPayload(int action, BlockPos pos, int index) implements CustomPacketPayload {
    public static final int TAKE = 0;
    public static final int TURN_IN = 1;
    public static final int ABANDON = 2;

    public static final CustomPacketPayload.Type<OrderActionPayload> TYPE =
            new CustomPacketPayload.Type<>(HeroicPaths.id("order_action"));
    public static final StreamCodec<ByteBuf, OrderActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, OrderActionPayload::action,
            BlockPos.STREAM_CODEC, OrderActionPayload::pos,
            ByteBufCodecs.VAR_INT, OrderActionPayload::index,
            OrderActionPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
