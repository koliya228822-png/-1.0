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
                    .sized(3.0f, 2.4f)
                    .eyeHeight(1.2f)
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

    /** Босс Глиняных копей: шестирукий колосс втрое выше игрока. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.ClayColossus>> CLAY_COLOSSUS =
            ENTITY_TYPES.register("clay_colossus", () -> EntityType.Builder
                    .<com.heroicpaths.entity.ClayColossus>of(com.heroicpaths.entity.ClayColossus::new, MobCategory.MONSTER)
                    .sized(2.4f, 5.4f)
                    .eyeHeight(4.7f)
                    .clientTrackingRange(12)
                    .build("clay_colossus"));

    /** Босс Ледяной цитадели: парящий Ледяной Лич. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.FrostLich>> FROST_LICH =
            ENTITY_TYPES.register("frost_lich", () -> EntityType.Builder
                    .<com.heroicpaths.entity.FrostLich>of(com.heroicpaths.entity.FrostLich::new, MobCategory.MONSTER)
                    .immuneTo(Blocks.POWDER_SNOW)
                    .sized(1.2f, 3.4f)
                    .eyeHeight(3.0f)
                    .clientTrackingRange(12)
                    .build("frost_lich"));

    // Нежить Ледяной цитадели (по мотивам Плети из Warcraft): чумной зомби, вурдалак, банши, лоскут плоти.
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.UndeadZombie>> UNDEAD_ZOMBIE =
            ENTITY_TYPES.register("undead_zombie", () -> EntityType.Builder
                    .<com.heroicpaths.entity.UndeadZombie>of(com.heroicpaths.entity.UndeadZombie::new, MobCategory.MONSTER)
                    .sized(0.6f, 1.95f).eyeHeight(1.74f).ridingOffset(-0.7f).clientTrackingRange(8).build("undead_zombie"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.Ghoul>> GHOUL =
            ENTITY_TYPES.register("ghoul", () -> EntityType.Builder
                    .<com.heroicpaths.entity.Ghoul>of(com.heroicpaths.entity.Ghoul::new, MobCategory.MONSTER)
                    .sized(0.8f, 1.4f).eyeHeight(1.1f).clientTrackingRange(8).build("ghoul"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.Banshee>> BANSHEE =
            ENTITY_TYPES.register("banshee", () -> EntityType.Builder
                    .<com.heroicpaths.entity.Banshee>of(com.heroicpaths.entity.Banshee::new, MobCategory.MONSTER)
                    .sized(0.7f, 1.9f).eyeHeight(1.6f).clientTrackingRange(8).build("banshee"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.Abomination>> ABOMINATION =
            ENTITY_TYPES.register("abomination", () -> EntityType.Builder
                    .<com.heroicpaths.entity.Abomination>of(com.heroicpaths.entity.Abomination::new, MobCategory.MONSTER)
                    .sized(1.6f, 2.4f).eyeHeight(2.0f).clientTrackingRange(8).build("abomination"));

    // Ледяные слуги Лича: ледяной голем, замороженный лучник, снеговик-громила.
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.IceGolem>> ICE_GOLEM =
            ENTITY_TYPES.register("ice_golem", () -> EntityType.Builder
                    .<com.heroicpaths.entity.IceGolem>of(com.heroicpaths.entity.IceGolem::new, MobCategory.MONSTER)
                    .immuneTo(Blocks.POWDER_SNOW)
                    .sized(0.9f, 1.5f).eyeHeight(1.2f).clientTrackingRange(8).build("ice_golem"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.FrozenArcher>> FROZEN_ARCHER =
            ENTITY_TYPES.register("frozen_archer", () -> EntityType.Builder
                    .<com.heroicpaths.entity.FrozenArcher>of(com.heroicpaths.entity.FrozenArcher::new, MobCategory.MONSTER)
                    .immuneTo(Blocks.POWDER_SNOW)
                    .sized(0.6f, 1.99f).eyeHeight(1.74f).ridingOffset(-0.7f).clientTrackingRange(8).build("frozen_archer"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.SnowBrute>> SNOW_BRUTE =
            ENTITY_TYPES.register("snow_brute", () -> EntityType.Builder
                    .<com.heroicpaths.entity.SnowBrute>of(com.heroicpaths.entity.SnowBrute::new, MobCategory.MONSTER)
                    .immuneTo(Blocks.POWDER_SNOW)
                    .sized(1.4f, 2.4f).eyeHeight(2.0f).clientTrackingRange(8).build("snow_brute"));

    // Глиняные големы Глиняных копей: ближнего боя, метатель и танк.
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.ClayGolem>> CLAY_GOLEM =
            ENTITY_TYPES.register("clay_golem", () -> EntityType.Builder
                    .<com.heroicpaths.entity.ClayGolem>of(com.heroicpaths.entity.ClayGolem::new, MobCategory.MONSTER)
                    .sized(0.8f, 1.3f).eyeHeight(1.1f).clientTrackingRange(8).build("clay_golem"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.ClayThrower>> CLAY_THROWER =
            ENTITY_TYPES.register("clay_thrower", () -> EntityType.Builder
                    .<com.heroicpaths.entity.ClayThrower>of(com.heroicpaths.entity.ClayThrower::new, MobCategory.MONSTER)
                    .sized(0.7f, 1.4f).eyeHeight(1.2f).clientTrackingRange(8).build("clay_thrower"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.ClayBrute>> CLAY_BRUTE =
            ENTITY_TYPES.register("clay_brute", () -> EntityType.Builder
                    .<com.heroicpaths.entity.ClayBrute>of(com.heroicpaths.entity.ClayBrute::new, MobCategory.MONSTER)
                    .sized(1.3f, 2.1f).eyeHeight(1.8f).clientTrackingRange(8).build("clay_brute"));
    /** Ком глины метателя. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.ClayShot>> CLAY_SHOT =
            ENTITY_TYPES.register("clay_shot", () -> EntityType.Builder
                    .<com.heroicpaths.entity.ClayShot>of(com.heroicpaths.entity.ClayShot::new, MobCategory.MISC)
                    .sized(0.25f, 0.25f).clientTrackingRange(4).updateInterval(10).build("clay_shot"));
    /** Глиняный валун колосса. */
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.ClayShot>> CLAY_BOULDER =
            ENTITY_TYPES.register("clay_boulder", () -> EntityType.Builder
                    .<com.heroicpaths.entity.ClayShot>of(com.heroicpaths.entity.ClayShot::new, MobCategory.MISC)
                    .sized(0.9f, 0.9f).clientTrackingRange(8).updateInterval(5).build("clay_boulder"));
    /** Отвалившаяся рука или кусок лат колосса (только зрелище). */
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.ClayDebris>> CLAY_DEBRIS =
            ENTITY_TYPES.register("clay_debris", () -> EntityType.Builder
                    .<com.heroicpaths.entity.ClayDebris>of(com.heroicpaths.entity.ClayDebris::new, MobCategory.MISC)
                    .sized(1.0f, 0.6f).clientTrackingRange(8).updateInterval(1).noSave().build("clay_debris"));

    // Паучье логово: пауки выводка Матери
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.BroodSpider>> BROOD_SPIDER =
            ENTITY_TYPES.register("brood_spider", () -> EntityType.Builder
                    .<com.heroicpaths.entity.BroodSpider>of(com.heroicpaths.entity.BroodSpider::new, MobCategory.MONSTER)
                    .sized(1.1f, 0.7f).eyeHeight(0.5f).clientTrackingRange(8).build("brood_spider"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.VenomSpitter>> VENOM_SPITTER =
            ENTITY_TYPES.register("venom_spitter", () -> EntityType.Builder
                    .<com.heroicpaths.entity.VenomSpitter>of(com.heroicpaths.entity.VenomSpitter::new, MobCategory.MONSTER)
                    .sized(1.1f, 1.0f).eyeHeight(0.6f).clientTrackingRange(8).build("venom_spitter"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.BroodTank>> BROOD_TANK =
            ENTITY_TYPES.register("brood_tank", () -> EntityType.Builder
                    .<com.heroicpaths.entity.BroodTank>of(com.heroicpaths.entity.BroodTank::new, MobCategory.MONSTER)
                    .sized(1.7f, 1.3f).eyeHeight(0.8f).clientTrackingRange(8).build("brood_tank"));
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.SpiderEggSac>> SPIDER_EGG_SAC =
            ENTITY_TYPES.register("spider_egg_sac", () -> EntityType.Builder
                    .<com.heroicpaths.entity.SpiderEggSac>of(com.heroicpaths.entity.SpiderEggSac::new, MobCategory.MONSTER)
                    .sized(0.8f, 0.6f).eyeHeight(0.3f).clientTrackingRange(8).build("spider_egg_sac"));

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

    // Стража деревень: мечник со щитом и лучник. MISC — не занимают лимит мирных животных.
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.VillageGuard>> GUARD_SWORDSMAN = guard("guard_swordsman");
    public static final DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.VillageGuard>> GUARD_ARCHER = guard("guard_archer");

    private static DeferredHolder<EntityType<?>, EntityType<com.heroicpaths.entity.VillageGuard>> guard(String name) {
        return ENTITY_TYPES.register(name, () -> EntityType.Builder
                .<com.heroicpaths.entity.VillageGuard>of(com.heroicpaths.entity.VillageGuard::new, MobCategory.MISC)
                .sized(0.6f, 1.95f).eyeHeight(1.62f).clientTrackingRange(10).build(name));
    }

    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(GUARD_SWORDSMAN.get(), com.heroicpaths.entity.VillageGuard.createAttributes().build());
        event.put(GUARD_ARCHER.get(), com.heroicpaths.entity.VillageGuard.createAttributes().build());
        event.put(CRYPT_KING.get(), CryptKing.createAttributes().build());
        event.put(FLAME_TYRANT.get(), FlameTyrant.createAttributes().build());
        event.put(FROST_LORD.get(), FrostLord.createAttributes().build());
        event.put(SPIDER_QUEEN.get(), com.heroicpaths.entity.SpiderQueen.createAttributes().build());
        event.put(DROWNED_PROPHET.get(), com.heroicpaths.entity.DrownedProphet.createAttributes().build());
        event.put(LICH.get(), com.heroicpaths.entity.Lich.createAttributes().build());
        event.put(CLAY_COLOSSUS.get(), com.heroicpaths.entity.ClayColossus.createAttributes().build());
        event.put(FROST_LICH.get(), com.heroicpaths.entity.FrostLich.createAttributes().build());
        event.put(UNDEAD_ZOMBIE.get(), com.heroicpaths.entity.UndeadZombie.createAttributes().build());
        event.put(GHOUL.get(), com.heroicpaths.entity.Ghoul.createAttributes().build());
        event.put(BANSHEE.get(), com.heroicpaths.entity.Banshee.createAttributes().build());
        event.put(ABOMINATION.get(), com.heroicpaths.entity.Abomination.createAttributes().build());
        event.put(ICE_GOLEM.get(), com.heroicpaths.entity.IceGolem.createAttributes().build());
        event.put(FROZEN_ARCHER.get(), com.heroicpaths.entity.FrozenArcher.createAttributes().build());
        event.put(SNOW_BRUTE.get(), com.heroicpaths.entity.SnowBrute.createAttributes().build());
        event.put(CLAY_GOLEM.get(), com.heroicpaths.entity.ClayGolem.createAttributes().build());
        event.put(CLAY_THROWER.get(), com.heroicpaths.entity.ClayThrower.createAttributes().build());
        event.put(CLAY_BRUTE.get(), com.heroicpaths.entity.ClayBrute.createAttributes().build());
        event.put(BROOD_SPIDER.get(), com.heroicpaths.entity.BroodSpider.createAttributes().build());
        event.put(VENOM_SPITTER.get(), com.heroicpaths.entity.VenomSpitter.createAttributes().build());
        event.put(BROOD_TANK.get(), com.heroicpaths.entity.BroodTank.createAttributes().build());
        event.put(SPIDER_EGG_SAC.get(), com.heroicpaths.entity.SpiderEggSac.createAttributes().build());
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
