package com.heroicpaths.item;

import com.heroicpaths.combat.WeaponType;
import com.heroicpaths.rpg.stat.Stat;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.minecraft.world.item.ShieldItem;
import org.jetbrains.annotations.Nullable;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Уникальный бонус предмета редкой редкости и выше. Большинство бонусов — прибавки к характеристикам,
 * которые действуют, пока предмет надет или в руке; ускорение натяжения лука и скорость добычи работают отдельно.
 */
public enum ItemTrait {
    ATTACK_SPEED(Stat.ATTACK_SPEED, 4, 8, Kind.MELEE),
    CRIT_CHANCE(Stat.CRIT_CHANCE, 3, 6, Kind.MELEE, Kind.RANGED),
    LIFE_STEAL(Stat.LIFE_STEAL, 1, 2, Kind.MELEE),
    BLEED_CHANCE(Stat.BLEED_CHANCE, 4, 8, Kind.SLASHING),
    STUN_CHANCE(Stat.STUN_CHANCE, 3, 6, Kind.CRUSHING),
    ARMOR_PIERCE(Stat.ARMOR_PIERCE, 5, 10, Kind.PIERCING),
    BOW_DAMAGE(Stat.BOW_DAMAGE, 5, 10, Kind.RANGED),
    DRAW_SPEED(null, 10, 20, Kind.RANGED),
    CAST_SPEED(Stat.COOLDOWN_REDUCTION, 4, 8, Kind.FOCUS),
    SPELL_POWER(Stat.SPELL_DAMAGE, 5, 10, Kind.FOCUS),
    MAX_RESOURCE(Stat.MAX_RESOURCE, 8, 16, Kind.FOCUS, Kind.ARMOR),
    RESOURCE_REGEN(Stat.RESOURCE_REGEN, 0.4f, 1.0f, Kind.FOCUS, Kind.ARMOR),
    MAX_HEALTH(Stat.MAX_HEALTH, 2, 4, Kind.ARMOR, Kind.SHIELD),
    DAMAGE_REDUCTION(Stat.DAMAGE_REDUCTION, 2, 4, Kind.ARMOR, Kind.SHIELD),
    MOVE_SPEED(Stat.MOVE_SPEED, 2, 4, Kind.ARMOR),
    DODGE(Stat.DODGE_CHANCE, 1, 3, Kind.ARMOR),
    MINING_SPEED(null, 1, 2, Kind.TOOL);

    /** К каким предметам подходит бонус. */
    public enum Kind { MELEE, SLASHING, CRUSHING, PIERCING, RANGED, FOCUS, ARMOR, SHIELD, TOOL }

    /** Выпавший бонус и его сила. */
    public record Roll(String trait, float value) {
        public static final Codec<Roll> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("trait").forGetter(Roll::trait),
                Codec.FLOAT.fieldOf("value").forGetter(Roll::value)
        ).apply(instance, Roll::new));
        public static final StreamCodec<ByteBuf, Roll> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Roll::trait,
                ByteBufCodecs.FLOAT, Roll::value,
                Roll::new);

        @Nullable
        public ItemTrait type() {
            return byId(trait);
        }
    }

    @Nullable
    private final Stat stat;
    private final float min;
    private final float max;
    private final Set<Kind> kinds;

    ItemTrait(@Nullable Stat stat, float min, float max, Kind... kinds) {
        this.stat = stat;
        this.min = min;
        this.max = max;
        this.kinds = EnumSet.copyOf(java.util.List.of(kinds));
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    @Nullable
    public Stat stat() {
        return stat;
    }

    public float min() {
        return min;
    }

    public float max() {
        return max;
    }

    public boolean fits(Set<Kind> itemKinds) {
        for (Kind kind : kinds) if (itemKinds.contains(kind)) return true;
        return false;
    }

    /** «✦ +6% к скорости атаки» */
    public MutableComponent describe(float value) {
        if (stat != null) return Component.literal("✦ ").append(stat.describe(value));
        return Component.literal("✦ ").append(Component.translatable("trait.heroicpaths." + id(), Stat.format(value)));
    }

    @Nullable
    public static ItemTrait byId(String id) {
        for (ItemTrait trait : values()) if (trait.id().equals(id)) return trait;
        return null;
    }

    /** Чем является предмет для бонусов: оружием какого типа, луком, фокусом, бронёй, щитом или инструментом. */
    public static Set<Kind> kindsOf(ItemStack stack) {
        Set<Kind> kinds = EnumSet.noneOf(Kind.class);
        if (stack.getItem() instanceof FocusItem) kinds.add(Kind.FOCUS);
        if (stack.getItem() instanceof ProjectileWeaponItem || stack.getItem() instanceof MusketItem) kinds.add(Kind.RANGED);
        if (stack.getItem() instanceof ArmorItem) kinds.add(Kind.ARMOR);
        if (stack.getItem() instanceof ShieldItem) kinds.add(Kind.SHIELD);
        WeaponType weapon = WeaponType.of(stack);
        if (weapon != null) {
            kinds.add(Kind.MELEE);
            kinds.add(switch (weapon) {
                case SLASHING -> Kind.SLASHING;
                case CRUSHING -> Kind.CRUSHING;
                case PIERCING -> Kind.PIERCING;
            });
        } else if (stack.getItem() instanceof DiggerItem) {
            kinds.add(Kind.TOOL);
        }
        return kinds;
    }
}
