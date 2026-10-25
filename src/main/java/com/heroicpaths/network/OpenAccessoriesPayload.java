package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Клиент → сервер: открыть окно аксессуаров. */
public record OpenAccessoriesPayload() implements CustomPacketPayload {
    public static final OpenAccessoriesPayload INSTANCE = new OpenAccessoriesPayload();
    public static final CustomPacketPayload.Type<OpenAccessoriesPayload> TYPE =
            new CustomPacketPayload.Type<>(HeroicPaths.id("open_accessories"));
    public static final StreamCodec<ByteBuf, OpenAccessoriesPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
