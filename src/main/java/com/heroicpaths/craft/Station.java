package com.heroicpaths.craft;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/** Станок ремесла: у каждого свой набор рецептов (поле "station" в рецепте). */
public enum Station {
    /** Кузнечный горн: сталь и дамаск, латы мечника и металлическое оружие. */
    FORGE,
    /** Дубильный чан: прочная и пропитанная кожа, лёгкая броня и луки. */
    TANNERY,
    /** Ткацкий станок: чародейская ткань и астральный шёлк, мантии мага. */
    LOOM,
    /** Рунный стол: руны из кристаллов, жезлы и посохи из рун и самоцветов. */
    RUNE_TABLE,
    /** Гранильный стол: огранка алмазов (камень может расколоться). */
    GEM_TABLE,
    /** Стол ювелира: ожерелья, серьги и кольца. */
    JEWELER_TABLE;

    public static final Codec<Station> CODEC = Codec.STRING.comapFlatMap(id -> {
        Station station = byId(id);
        return station == null ? DataResult.error(() -> "Unknown station " + id) : DataResult.success(station);
    }, Station::id);

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public MutableComponent displayName() {
        return Component.translatable("station.heroicpaths." + id());
    }

    @Nullable
    public static Station byId(String id) {
        for (Station station : values()) if (station.id().equals(id)) return station;
        return null;
    }
}
