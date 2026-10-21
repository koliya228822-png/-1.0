package com.heroicpaths.network;

import com.heroicpaths.HeroicPaths;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/** Сервер → клиент: состав группы для панели на экране. Пустой список — игрок не в группе. */
public record PartySyncPayload(List<Member> members) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PartySyncPayload> TYPE =
            new CustomPacketPayload.Type<>(HeroicPaths.id("party_sync"));
    public static final StreamCodec<FriendlyByteBuf, PartySyncPayload> STREAM_CODEC =
            StreamCodec.of(PartySyncPayload::write, PartySyncPayload::read);

    public record Member(String name, int level, float health, float maxHealth, boolean online, boolean leader, int color) {}

    private static void write(FriendlyByteBuf buf, PartySyncPayload payload) {
        buf.writeVarInt(payload.members.size());
        for (Member member : payload.members) {
            buf.writeUtf(member.name(), 64);
            buf.writeVarInt(member.level());
            buf.writeFloat(member.health());
            buf.writeFloat(member.maxHealth());
            buf.writeBoolean(member.online());
            buf.writeBoolean(member.leader());
            buf.writeInt(member.color());
        }
    }

    private static PartySyncPayload read(FriendlyByteBuf buf) {
        int size = Math.min(buf.readVarInt(), 16);
        List<Member> members = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            members.add(new Member(buf.readUtf(64), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                    buf.readBoolean(), buf.readBoolean(), buf.readInt()));
        }
        return new PartySyncPayload(members);
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
