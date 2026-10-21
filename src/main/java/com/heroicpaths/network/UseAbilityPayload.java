package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Клиент → сервер: нажата клавиша способности (слот 0..2). */
public record UseAbilityPayload(int slot) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<UseAbilityPayload> TYPE =
            new CustomPacketPayload.Type<>(HeroicPaths.id("use_ability"));
    public static final StreamCodec<ByteBuf, UseAbilityPayload> STREAM_CODEC =
            ByteBufCodecs.VAR_INT.map(UseAbilityPayload::new, UseAbilityPayload::slot);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
