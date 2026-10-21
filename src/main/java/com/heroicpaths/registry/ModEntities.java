package com.heroicpaths.registry;

import com.heroicpaths.HeroicPaths;
import com.heroicpaths.entity.CryptKing;
import com.heroicpaths.entity.FlameTyrant;
import com.heroicpaths.entity.FrostLord;
import com.heroicpaths.entity.MusketBall;
import com.heroicpaths.entity.SpellProjectile;
import com.heroicpaths.minion.CompanionBear;
import com.heroicpaths.minion.CompanionBoar;
import com.heroicpaths.minion.CompanionWolf;
import com.heroicpaths.minion.SummonedGolem;
import com.heroicpaths.minion.SummonedSkeleton;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.PolarBear;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.monster.hoglin.Hoglin;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, HeroicPaths.MODID);

    public static final DeferredHolder<EntityType<?>, EntityType<SpellProjectile>> SPELL_PROJECTILE =
            ENTITY_TYPES.register("spell_projectile", () -> EntityType.Builder
                    .<SpellProjectile>of(SpellProjectile::new, MobCategory.MISC)
                    .sized(0.3125f, 0.3125f)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build("spell_projectile"));

    /** Магическая стрела жезла или посоха: медленная, самонаводящаяся. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.MagicBoltEntity>> MAGIC_BOLT =
            ENTITY_TYPES.register("magic_bolt", () -> EntityType.Builder
                    .<com.heroicpaths.entity.MagicBoltEntity>of(com.heroicpaths.entity.MagicBoltEntity::new, MobCategory.MISC)
                    .sized(0.3f, 0.3f)
                    .clientTrackingRange(4)
                    .updateInterval(2)
                    .build("magic_bolt"));

    /** Метеор мага: падающая раскалённая глыба (только зрелище, урон делает умение). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.MeteorEntity>> METEOR =
            ENTITY_TYPES.register("meteor", () -> EntityType.Builder
                    .<com.heroicpaths.entity.MeteorEntity>of(com.heroicpaths.entity.MeteorEntity::new, MobCategory.MISC)
                    .sized(1f, 1f)
                    .clientTrackingRange(10)
                    .updateInterval(20)
                    .noSave()
                    .build("meteor"));

    /** Босс данжа: в полтора раза больше скелета-иссушителя. */
    public static final DeferredHolder<EntityType<?>, EntityType<CryptKing>> CRYPT_KING =
            ENTITY_TYPES.register("crypt_king", () -> EntityType.Builder
                    .<CryptKing>of(CryptKing::new, MobCategory.MONSTER)
                    .fireImmune()
                    .immuneTo(Blocks.WITHER_ROSE)
                    .sized(1.05f, 3.6f)
                    .eyeHeight(3.15f)
                    .clientTrackingRange(10)
                    .build("crypt_king"));

    /** Босс Огненной кузни: ифрит в 2.2 раза больше обычного. */
    public static final DeferredHolder<EntityType<?>, EntityType<FlameTyrant>> FLAME_TYRANT =
            ENTITY_TYPES.register("flame_tyrant", () -> EntityType.Builder
                    .<FlameTyrant>of(FlameTyrant::new, MobCategory.MONSTER)
                    .fireImmune()
                    .sized(1.3f, 3.9f)
                    .clientTrackingRange(10)
                    .build("flame_tyrant"));

    /** Босс Ледяных чертогов: зимогор в 1.8 раза больше обычного. */
    public static final DeferredHolder<EntityType<?>, EntityType<FrostLord>> FROST_LORD =
            ENTITY_TYPES.register("frost_lord", () -> EntityType.Builder
                    .<FrostLord>of(FrostLord::new, MobCategory.MONSTER)
                    .immuneTo(Blocks.POWDER_SNOW)
                    .sized(1.1f, 3.6f)
                    .eyeHeight(3.1f)
                    .clientTrackingRange(10)
                    .build("frost_lord"));

    /** Босс Паучьего логова: паук вдвое больше обычного. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.SpiderQueen>> SPIDER_QUEEN =
            ENTITY_TYPES.register("spider_queen", () -> EntityType.Builder
                    .<com.heroicpaths.entity.SpiderQueen>of(com.heroicpaths.entity.SpiderQueen::new, MobCategory.MONSTER)
                    .sized(2.8f, 1.8f)
                    .eyeHeight(1.3f)
                    .clientTrackingRange(10)
                    .build("spider_queen"));

    /** Босс Затопленного храма: утопленник в 1.7 раза больше обычного. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.DrownedProphet>> DROWNED_PROPHET =
            ENTITY_TYPES.register("drowned_prophet", () -> EntityType.Builder
                    .<com.heroicpaths.entity.DrownedProphet>of(com.heroicpaths.entity.DrownedProphet::new, MobCategory.MONSTER)
                    .sized(1.0f, 3.3f)
                    .eyeHeight(2.9f)
                    .clientTrackingRange(10)
                    .build("drowned_prophet"));

    /** Босс Некрополя: скелет-иссушитель в 1.35 раза больше обычного с посохом архимага. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.Lich>> LICH =
            ENTITY_TYPES.register("lich", () -> EntityType.Builder
                    .<com.heroicpaths.entity.Lich>of(com.heroicpaths.entity.Lich::new, MobCategory.MONSTER)
                    .fireImmune()
                    .immuneTo(Blocks.WITHER_ROSE)
                    .sized(0.95f, 3.25f)
                    .eyeHeight(2.85f)
                    .clientTrackingRange(10)
                    .build("lich"));

    // Зверь охотника растёт вместе с ним: пёс → волк → кабан → медведь → совомедведь.
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionWolf>> COMPANION_DOG = wolf("companion_dog");
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionWolf>> COMPANION_WOLF = wolf("companion_wolf");
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionBoar>> COMPANION_BOAR =
            ENTITY_TYPES.register("companion_boar", () -> EntityType.Builder
                    .<CompanionBoar>of(CompanionBoar::new, MobCategory.MISC)
                    .sized(1.3965f, 1.4f).clientTrackingRange(10).build("companion_boar"));
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionBear>> COMPANION_BEAR = bear("companion_bear");
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionBear>> COMPANION_OWLBEAR = bear("companion_owlbear");
    // Существа призывателя
    public static final DeferredHolder<EntityType<?>, EntityType<CompanionWolf>> SPIRIT_WOLF = wolf("spirit_wolf");
    public static final DeferredHolder<EntityType<?>, EntityType<SummonedSkeleton>> SUMMONED_SKELETON =
            ENTITY_TYPES.register("summoned_skeleton", () -> EntityType.Builder
                    .<SummonedSkeleton>of(SummonedSkeleton::new, MobCategory.MISC)
                    .sized(0.6f, 1.99f).eyeHeight(1.74f).ridingOffset(-0.7f).clientTrackingRange(8).build("summoned_skeleton"));
    public static final DeferredHolder<EntityType<?>, EntityType<SummonedGolem>> SUMMONED_GOLEM =
            ENTITY_TYPES.register("summoned_golem", () -> EntityType.Builder
                    .<SummonedGolem>of(SummonedGolem::new, MobCategory.MISC)
                    .sized(1.4f, 2.7f).clientTrackingRange(10).build("summoned_golem"));
    /** Пуля мушкета. */
    public static final DeferredHolder<EntityType<?>, EntityType<MusketBall>> MUSKET_BALL =
            ENTITY_TYPES.register("musket_ball", () -> EntityType.Builder
                    .<MusketBall>of(MusketBall::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(20).build("musket_ball"));

    private ModEntities() {}

    private static DeferredHolder<EntityType<?>, EntityType<CompanionWolf>> wolf(String name) {
        return ENTITY_TYPES.register(name, () -> EntityType.Builder
                .<CompanionWolf>of(CompanionWolf::new, MobCategory.MISC)
                .sized(0.6f, 0.85f).eyeHeight(0.68f).clientTrackingRange(10).build(name));
    }

    private static DeferredHolder<EntityType<?>, EntityType<CompanionBear>> bear(String name) {
        return ENTITY_TYPES.register(name, () -> EntityType.Builder
                .<CompanionBear>of(CompanionBear::new, MobCategory.MISC)
                .sized(1.4f, 1.4f).clientTrackingRange(10).build(name));
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(CRYPT_KING.get(), CryptKing.createAttributes().build());
        event.put(FLAME_TYRANT.get(), FlameTyrant.createAttributes().build());
        event.put(FROST_LORD.get(), FrostLord.createAttributes().build());
        event.put(SPIDER_QUEEN.get(), com.heroicpaths.entity.SpiderQueen.createAttributes().build());
        event.put(DROWNED_PROPHET.get(), com.heroicpaths.entity.DrownedProphet.createAttributes().build());
        event.put(LICH.get(), com.heroicpaths.entity.Lich.createAttributes().build());
        event.put(COMPANION_DOG.get(), Wolf.createAttributes().build());
        event.put(COMPANION_WOLF.get(), Wolf.createAttributes().build());
        event.put(SPIRIT_WOLF.get(), Wolf.createAttributes().build());
        event.put(COMPANION_BOAR.get(), Hoglin.createAttributes().build());
        event.put(COMPANION_BEAR.get(), PolarBear.createAttributes().build());
        event.put(COMPANION_OWLBEAR.get(), PolarBear.createAttributes().build());
        event.put(SUMMONED_SKELETON.get(), AbstractSkeleton.createAttributes().build());
        event.put(SUMMONED_GOLEM.get(), IronGolem.createAttributes().build());
    }
}
