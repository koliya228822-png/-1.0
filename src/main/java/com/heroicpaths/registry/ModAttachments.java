package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.gear.KeptGear;
import com.heroicpaths.rpg.RpgData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, HeroicPaths.MODID);

    /** RPG-прогресс игрока: класс, уровень, навыки. Сохраняется после смерти. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<RpgData>> RPG_DATA =
            ATTACHMENT_TYPES.register("rpg_data", () -> AttachmentType.serializable(RpgData::new).copyOnDeath().build());

    /** Оружие и броня, снятые при смерти: возвращаются после возрождения. */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<KeptGear>> KEPT_GEAR =
            ATTACHMENT_TYPES.register("kept_gear", () -> AttachmentType.serializable(KeptGear::new).copyOnDeath().build());

    private ModAttachments() {}
}
