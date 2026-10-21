package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Сервер → клиент: открыть доску заказов; offers — заказы доски на сегодня. */
public record OpenOrderBoardPayload(BlockPos pos, CompoundTag offers) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenOrderBoardPayload> TYPE =
            new CustomPacketPayload.Type<>(HeroicPaths.id("open_order_board"));
    public static final StreamCodec<ByteBuf, OpenOrderBoardPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, OpenOrderBoardPayload::pos,
            ByteBufCodecs.COMPOUND_TAG, OpenOrderBoardPayload::offers,
            OpenOrderBoardPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
