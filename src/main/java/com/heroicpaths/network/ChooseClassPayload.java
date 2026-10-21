package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Клиент → сервер: игрок выбрал класс и подкласс в меню. */
public record ChooseClassPayload(int classId, String subclass) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ChooseClassPayload> TYPE =
            new CustomPacketPayload.Type<>(HeroicPaths.id("choose_class"));
    public static final StreamCodec<ByteBuf, ChooseClassPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ChooseClassPayload::classId,
            ByteBufCodecs.STRING_UTF8, ChooseClassPayload::subclass,
            ChooseClassPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
