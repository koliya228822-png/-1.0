package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Сервер → клиент: открыть экран доски заданий гильдии. */
public record OpenQuestBoardPayload() implements CustomPacketPayload {
    public static final OpenQuestBoardPayload INSTANCE = new OpenQuestBoardPayload();
    public static final CustomPacketPayload.Type<OpenQuestBoardPayload> TYPE =
            new CustomPacketPayload.Type<>(HeroicPaths.id("open_quest_board"));
    public static final StreamCodec<ByteBuf, OpenQuestBoardPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
