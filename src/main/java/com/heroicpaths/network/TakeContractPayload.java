package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Клиент → сервер: взять контракт на данж. Размер группы сервер берёт из состава группы игрока. */
public record TakeContractPayload(String dungeon) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<TakeContractPayload> TYPE =
            new CustomPacketPayload.Type<>(HeroicPaths.id("take_contract"));
    public static final StreamCodec<ByteBuf, TakeContractPayload> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(TakeContractPayload::new, TakeContractPayload::dungeon);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
