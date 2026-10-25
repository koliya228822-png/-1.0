package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Стол зачарований (стол умений). Сервер → клиент: открыть окно (value — сколько полок умений вокруг).
 * Клиент → сервер: создать книгу (ability пусто — случайная книга умения, иначе — книга этого уникального умения).
 */
public record SkillTablePayload(BlockPos pos, String ability, int value) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SkillTablePayload> TYPE = new CustomPacketPayload.Type<>(HeroicPaths.id("skill_table"));
    public static final StreamCodec<ByteBuf, SkillTablePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, SkillTablePayload::pos,
            ByteBufCodecs.STRING_UTF8, SkillTablePayload::ability,
            ByteBufCodecs.VAR_INT, SkillTablePayload::value,
            SkillTablePayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
